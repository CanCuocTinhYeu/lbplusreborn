package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot
import net.ccbluex.liquidbounce.features.module.modules.player.Blink
import net.ccbluex.liquidbounce.features.module.modules.render.FreeCam
import net.ccbluex.liquidbounce.features.module.modules.world.Scaffold
import net.ccbluex.liquidbounce.features.module.modules.world.Teams
import net.ccbluex.liquidbounce.utils.EntityUtils
import net.ccbluex.liquidbounce.utils.PacketUtils
import net.ccbluex.liquidbounce.utils.Rotation
import net.ccbluex.liquidbounce.utils.RotationUtils
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.misc.RandomUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.utils.timer.TimerUtils
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11
import java.util.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@ModuleInfo(
    name = "KillAura",
    category = ModuleCategory.COMBAT,
    description = "Auto-attacks entities"
)
class KillAura : Module() {
    private val maxCPS: IntegerValue = object : IntegerValue("MaxCPS", 10, 1, 20) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = minCPS.get()
            if (i > newValue) set(i)

            attackDelay = TimerUtils.randomClickDelay(minCPS.get(), this.get())
        }
    }

    private val minCPS: IntegerValue = object : IntegerValue("MinCPS", 8, 1, 20) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = maxCPS.get()
            if (i < newValue) set(i)

            attackDelay = TimerUtils.randomClickDelay(this.get(), maxCPS.get())
        }
    }

    private val reachMode by ListValue("CheckTargetDistance", arrayOf("Old", "New"), "New")

    private val rotationRange : FloatValue = object : FloatValue("Rotation-Range", 7.0f, 2.0f, 10.0f){
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue < range.get()) set(range.get())
        }
    }
    private val swingRange : FloatValue = object : FloatValue("Swing-Range", 6.0f, 2.0f, 10.0f){
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue > rotationRange.get()) set(rotationRange.get())
        }
    }
    private val range : FloatValue = object : FloatValue("Attack-Range", 4.0f, 2.0f, 10.0f){
        override fun onChanged(oldValue: Float, newValue: Float) {
            if (newValue > swingRange.get()) set(swingRange.get())
        }
    }
    private val throughWalls by BoolValue("ThroughWalls", true)

    private val rotate = BoolValue("Rotate",true)
    private val silentRotation by BoolValue("SilentRotation", true) { rotate.get() }
    private val rotationMode = ListValue("RotateMode", arrayOf("Normal", "LiquidBounce","NearestPoint","Advanced"), "LiquidBounce") { rotate.get() }
    private val yawMaxTurnSpeed: FloatValue =
        object : FloatValue("YawMaxTurnSpeed", 180f, 0f, 180f, "°", { rotate.get() }) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = yawMinTurnSpeed.get()
                if (i > newValue) set(i)
            }
        }

    private val yawMinTurnSpeed: FloatValue =
        object : FloatValue("YawMinTurnSpeed", 180f, 0f, 180f, "°", { rotate.get() }) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = yawMaxTurnSpeed.get()
                if (i < newValue) set(i)
            }
        }
    private val pitchMaxTurnSpeed: FloatValue =
        object : FloatValue("PitchMaxTurnSpeed", 180f, 0f, 180f, "°", { rotate.get() }) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = pitchMinTurnSpeed.get()
                if (i > newValue) set(i)
            }
        }
    private val pitchMinTurnSpeed: FloatValue =
        object : FloatValue("PitchMinTurnSpeed", 180f, 0f, 180f, "°", { rotate.get() }) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = pitchMaxTurnSpeed.get()
                if (i < newValue) set(i)
            }
        }
    
    private val keepTicks = IntegerValue("KeepTicks", 20, 0,20) { rotate.get() }
    private val angleThresholdUntilReset = FloatValue("AngleThresholdUntilReset", 5f, 0.1f,180f) { rotate.get() }
    private val resetMaxTurnSpeed: FloatValue =
        object : FloatValue("ResetMaxTurnSpeed", 180f, 0f, 180f, "°", { rotate.get() }) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = resetMinTurnSpeed.get()
                if (i > newValue) set(i)
            }
        }
    private val resetMinTurnSpeed: FloatValue =
        object : FloatValue("ResetMinTurnSpeed", 180f, 0f, 180f, "°", { rotate.get()}) {
            override fun onChanged(oldValue: Float, newValue: Float) {
                val i = resetMaxTurnSpeed.get()
                if (i < newValue) set(i)
            }
        }

    private val priority by ListValue(
        "Priority",
        arrayOf(
            "Health",
            "Distance",
            "Direction",
            "LivingTime",
            "Armor",
            "HurtResistance",
            "HurtTime",
            "HealthAbsorption",
            "RegenAmplifier"
        ),
        "Distance"
    )

    private val smartHurtTime = BoolValue("SmartHurttime", false)
    private val smartHurtTimeValue by IntegerValue("SmartHurttimeValue", 7, 0, 10) { smartHurtTime.get() }
    private val hurtTime by IntegerValue("HurtTime", 10, 0, 10) { !smartHurtTime.get() }

    private val smartAttackValue = BoolValue("SmartAttack", false)
    private val noSpamClick = BoolValue("NoSpamClick", true)
    private val extraRandomCPS = ListValue("ExtraCPSRandomization", arrayOf("Off", "Simple", "RangeBase"), "Off")
    private val cpsReduceValue = BoolValue("CPSReduceVelocity", false)

    private val autoBlockMode by ListValue("AutoBlock", arrayOf("None", "Vanilla","HypixelBlink"), "None")
    private val verusAutoBlockValue by BoolValue("VerusAutoBlock", false) { autoBlockMode == "Vanilla" }
    private val interactAutoBlockValue by BoolValue("InteractAutoBlock", false) { autoBlockMode == "Vanilla" }
    private val blockRate by IntegerValue("BlockRate", 100, 1,100) { autoBlockMode == "Vanilla" }

    private val hitableCheck = BoolValue("HitableCheck", true)

    private val jitter = BoolValue("Jitter", false)
    private val jitterStrengthYaw = FloatValue("JitterStrengthYaw", 10.0f, 0.0f, 20.0f) { jitter.get() }
    private val jitterStrengthPitch = FloatValue("JitterStrengthPitch", 10.0f, 0.0f, 20.0f) { jitter.get() }

    private val noInvAttack by BoolValue("NoInvAttack", true)
    private val noBlink by BoolValue("NoBlink", true)
    private val noScaff by BoolValue("NoScaffold", true)

    private val circle by BoolValue("Circle", false)
    private val circleAccuracy by IntegerValue("Accuracy", 59, 0, 59) { circle }
    private val circleThickness by FloatValue("Thickness", 2f, 0f, 20f) { circle }
    private val circleRed by IntegerValue("Red", 255, 0, 255) { circle }
    private val circleGreen by IntegerValue("Green", 255, 0, 255) { circle }
    private val circleBlue by IntegerValue("Blue", 255, 0, 255) { circle }
    private val circleAlpha by IntegerValue("Alpha", 255, 0, 255) { circle }

    private var rotations: Rotation? = null
    var target: EntityLivingBase? = null
    private val prevTargetEntities = mutableListOf<Int>()
    var blockingStatus = false
    private var verusBlocking = false

    private val timerAttack: MSTimer = MSTimer()
    private var attackDelay = 0L
    var clicks = 0
    var hitable = false

    var unb2 = false
    var delay = 0
    var started = false
    var stage = 0

    override fun onDisable() {
        target = null
        prevTargetEntities.clear()
        timerAttack.reset()
        stopBlocking()
        if (verusBlocking && !blockingStatus && !mc.thePlayer.isBlocking) {
            verusBlocking = false
            if (verusAutoBlockValue)
                PacketUtils.sendPacketNoEvent(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    )
                )
        }
        if(autoBlockMode == "HypixelBlink"){
            LiquidBounce.moduleManager[Blink::class.java]!!.state = false
        }
        started = false
        unb2 = false
        delay = 0
        stage = 0
        hitable = false
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (cancelRun)
            return
        val packet = event.packet
        if (verusBlocking
            && ((packet is C07PacketPlayerDigging
                    && packet.status == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM)
                    || packet is C08PacketPlayerBlockPlacement)
            && verusAutoBlockValue
        )
            event.cancelEvent()

        if (packet is C09PacketHeldItemChange)
            verusBlocking = false
    }


    @EventTarget
    fun onUpdate(event: UpdateEvent) {

        if(target == null){
            stopBlocking()
        }

        if (cancelRun)
            return

        if (blockingStatus || mc.thePlayer.isBlocking)
            verusBlocking = true
        else if (verusBlocking) {
            verusBlocking = false
            if (verusAutoBlockValue)
                PacketUtils.sendPacketNoEvent(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    )
                )
        }

        updateTarget()
        if (target != null) {

            hitable = if (hitableCheck.get())
                target?.let { RotationUtils.isFaced(it, rotationRange.get().toDouble()) } == true
            else true

            if (cpsReduceValue.get() && mc.thePlayer.hurtTime > 8) {
                clicks += 4
            }

            if (isVisible(target!!.positionVector) || isVisible(
                    getNearestPointBB(
                        mc.thePlayer.getPositionEyes(1f),
                        target!!.entityBoundingBox
                    )
                ) || mc.thePlayer.canEntityBeSeen(target) || throughWalls
            ) {

                if (rotate.get())
                    rotate(target!!)

                if (mc.thePlayer.isBlocking || blockingStatus)
                    stopBlocking()

                if (noSpamClick.get()) {
                   if (clicks > 0) {
                      //LiquidBounce.eventManager.callEvent(AttackEvent(target))
                      if (getSwingRange()) {
                          mc.thePlayer.swingItem()
                      }
                      if (getAttackRange() && hitable) {
                          mc.playerController.attackEntity(mc.thePlayer, target)
                      }
                      clicks = 0
            }
        } else {
            while (clicks > 0) {
               //LiquidBounce.eventManager.callEvent(AttackEvent(target))
               if (getSwingRange()) {
                   mc.thePlayer.swingItem()
               }
               if (getAttackRange() && hitable) {
                   mc.playerController.attackEntity(mc.thePlayer, target)
               }
               clicks--
               }
            }

                if (autoBlockMode == "Vanilla" && canBlock) {
                    startBlocking(target!!, interactAutoBlockValue)
                }
            }
        }
    }

    fun getRotationRange(entity:Entity):Boolean{
        return reachMode == "Old" && mc.thePlayer.getDistanceToEntityBox(entity) - 0.125 <= rotationRange.get() || reachMode == "New" && mc.thePlayer.eyes.distanceTo(
            entity.eyes
        ) - 0.5 <= rotationRange.get()
    }

    fun getSwingRange() : Boolean {
        return reachMode == "Old" && mc.thePlayer.getDistanceToEntityBox(target!!) - 0.125 <= swingRange.get() || reachMode == "New" && mc.thePlayer.eyes.distanceTo(
            target?.eyes
        ) - 0.5 <= swingRange.get()
    }

    fun getAttackRange():Boolean{
        return reachMode == "Old" && mc.thePlayer.getDistanceToEntityBox(target!!) - 0.125 <= range.get() || reachMode == "New" && mc.thePlayer.eyes.distanceTo(
            target?.eyes
        ) - 0.5 <= range.get()
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {

        if (target != null && event.eventState == EventState.PRE && canBlock && autoBlockMode == "HypixelBlink" && mc.thePlayer.getDistanceToEntityBox(
                target!!
            ) <= range.get()
        ) {
            val blink = LiquidBounce.moduleManager[Blink::class.java]!!
            unb2 = false
            delay = 0
            started = true
            stage += 1

            if (stage == 1) {
                blink.state = true
                stopBlocking()
            } else if (stage == 2) {
                mc.thePlayer.swingItem()
                mc.netHandler.addToSendQueue(C02PacketUseEntity(target, Action.ATTACK))
                mc.netHandler.addToSendQueue(C02PacketUseEntity(target, Action.INTERACT))
                blink.state = false
                startBlocking(target!!, false)
                stage = 0
            }


            if (started && target == null) {
                if (canBlock) {
                    unb2 = true
                }
                started = false
                delay = 0
                stage = 0
            }

            if (unb2) {
                delay += 1
                if (delay == 2) {
                    PacketUtils.sendPacketNoEvent(C07PacketPlayerDigging())
                    unb2 = false
                    delay = 0
                }
            }
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (circle) {
            GL11.glPushMatrix()
            GL11.glTranslated(
                mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX,
                mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY,
                mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ
            )
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            GL11.glLineWidth(circleThickness)
            GL11.glColor4f(
                circleRed.toFloat() / 255.0F,
                circleGreen.toFloat() / 255.0F,
                circleBlue.toFloat() / 255.0F,
                circleAlpha.toFloat() / 255.0F
            )
            GL11.glRotatef(90F, 1F, 0F, 0F)
            GL11.glBegin(GL11.GL_LINE_STRIP)

            for (i in 0..360 step 60 - circleAccuracy) { // You can change circle accuracy  (60 - accuracy)
                GL11.glVertex2f(
                    cos(i * Math.PI / 180.0).toFloat() * range.get(),
                    (sin(i * Math.PI / 180.0).toFloat() * range.get())
                )
            }

            GL11.glEnd()

            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)

            GL11.glPopMatrix()
        }

        if (cancelRun) {
            target = null
            stopBlocking()
            return
        }

        target ?: return

        var timeAdder = if (!smartAttackValue.get() || mc.thePlayer.hurtTime != 0 || (target !is EntityLivingBase || (target as EntityLivingBase).hurtTime <= (3 + (mc.thePlayer.getPing() / 50.0).toInt()))) 0 else 500

        if (extraRandomCPS.get() == "Simple") {
            timeAdder += RandomUtils.nextInt(-50, 200)
        } else if (extraRandomCPS.get() == "RangeBase") {
            val distance = mc.thePlayer.getLookDistanceToEntityBox(target!!)
            if (target is EntityLivingBase && distance in (range.get() + 0.01f)..(range.get() + 0.4f)) {
                timeAdder += 200
            }
        }

        if (target != null && timerAttack.hasTimePassed(attackDelay + timeAdder) &&
            (target !is EntityLivingBase || (!smartHurtTime.get() && (target as EntityLivingBase).hurtTime <= hurtTime) || (smartHurtTime.get() && ((target as EntityLivingBase).hurtTime == 0 || (target as EntityLivingBase).hurtTime > smartHurtTimeValue || mc.thePlayer.hurtTime != 0)))) {
            ++clicks
            timerAttack.reset()
            attackDelay = TimerUtils.randomClickDelay(minCPS.get(), maxCPS.get())
        }
    }

    private fun rotate(entity:Entity) {
        var boundingBox = entity.hitBox

        when (rotationMode.get()) {
            "Normal" -> {
                rotations = RotationUtils.limitAngleChange(
                    RotationUtils.serverRotation, RotationUtils.getAngles(entity)!!, RandomUtils.nextFloat(yawMinTurnSpeed.get(), yawMaxTurnSpeed.get()),RandomUtils.nextFloat(pitchMinTurnSpeed.get(), pitchMaxTurnSpeed.get())
                )
            }
            
            "LiquidBounce" -> {
                rotations = RotationUtils.limitAngleChange(
                    RotationUtils.serverRotation, RotationUtils.searchCenter(
                        boundingBox,
                        outborder = false,
                        random = false,
                        predict = false,
                        throughWalls = true,
                        distance = rotationRange.get(),
                        randomMultiply = 0f,
                        newRandom = false
                    ), RandomUtils.nextFloat(yawMinTurnSpeed.get(), yawMaxTurnSpeed.get()),RandomUtils.nextFloat(pitchMinTurnSpeed.get(), pitchMaxTurnSpeed.get())
                )
            }
            "NearestPoint" -> {
                rotations = RotationUtils.limitAngleChange(
                    RotationUtils.serverRotation, RotationUtils.OtherRotation(
                        boundingBox,
                        getNearestPointBB(mc.thePlayer.getPositionEyes(1f), entity.entityBoundingBox),
                        predict = false,
                        throughWalls = true,
                        distance = rotationRange.get()
                    ), RandomUtils.nextFloat(yawMinTurnSpeed.get(), yawMaxTurnSpeed.get()),RandomUtils.nextFloat(pitchMinTurnSpeed.get(), pitchMaxTurnSpeed.get())
                )
            }
            "Advanced" -> {
                val (_, rotation) = RotationUtils.newSearchCenter(
                    boundingBox,
                    outborder = false,
                    random = false,
                    predict = false, throughWalls = true,
                    discoverRange = rotationRange.get(),
                    hitRange = range.get()
                ) ?: return
                rotations = RotationUtils.limitAngleChange(
                    RotationUtils.serverRotation, rotation, RandomUtils.nextFloat(yawMinTurnSpeed.get(), yawMaxTurnSpeed.get()),RandomUtils.nextFloat(pitchMinTurnSpeed.get(), pitchMaxTurnSpeed.get())
                )
            }
        }

        val random = Random()
        val jitterYaw: Float = (random.nextFloat() * 2 - 1) * jitterStrengthYaw.get()
        val jitterPitch: Float = (random.nextFloat() * 2 - 1) * jitterStrengthPitch.get()
        rotations?.yaw = rotations?.yaw?.plus(if (jitter.get()) jitterYaw else 0f)!!
        rotations?.pitch = rotations?.pitch?.plus(if (jitter.get()) jitterPitch else 0f)!!

        //RotationUtils.setTargetRotation(rotations!!)
        if(silentRotation) {
            RotationUtils.setTargetRotation(
                rotations!!.fixedSensitivity(),
                keepTicks.get(),
                resetMinTurnSpeed.get() to resetMaxTurnSpeed.get(),
                angleThresholdUntilReset.get()
            )
        } else {
            rotations!!.fixedSensitivity().toPlayer(mc.thePlayer)
        }
    }

    private fun updateTarget() {

        // Find possible targets
        val targets = mutableListOf<EntityLivingBase>()

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !isEnemy(entity))
                continue

            val distance = mc.thePlayer.getDistanceToEntityBox(entity)

            if (getRotationRange(entity) && entity.hurtTime <= hurtTime)
                targets.add(entity)
        }

        // Sort targets by priority
        when (priority.lowercase(Locale.getDefault())) {
            "distance" -> targets.sortBy { mc.thePlayer.getDistanceToEntityBox(it) } // Sort by distance
            "health" -> targets.sortBy { it.health } // Sort by health
            "direction" -> targets.sortBy { RotationUtils.getRotationDifference(it) } // Sort by FOV
            "livingtime" -> targets.sortBy { -it.ticksExisted } // Sort by existence
            "hurtresistance" -> targets.sortBy { it.hurtResistantTime } // Sort by armor hurt time
            "hurttime" -> targets.sortBy { it.hurtTime } // Sort by hurt time
            "healthabsorption" -> targets.sortBy { it.health + it.absorptionAmount } // Sort by full health with absorption effect
            "regenamplifier" -> targets.sortBy { if (it.isPotionActive(Potion.regeneration)) it.getActivePotionEffect(
                Potion.regeneration).amplifier else -1 }
        }

        var found = false

        // Find best target
        for (entity in targets) {
            // Set target to current entity
            target = entity
            found = true
            break
        }


        if (!found) {
            target = null
        }
    }

    private fun isEnemy(entity: Entity?): Boolean {
        if (entity is EntityLivingBase && (EntityUtils.targetDead || isAlive(entity)) && entity != mc.thePlayer) {
            if (!EntityUtils.targetInvisible && entity.isInvisible())
                return false

            if (EntityUtils.targetPlayer && entity is EntityPlayer) {
                if (entity.isSpectator || AntiBot.isBot(entity))
                    return false

                if (EntityUtils.isFriend(entity) && !LiquidBounce.moduleManager[NoFriends::class.java]!!.state)
                    return false

                val teams = LiquidBounce.moduleManager[Teams::class.java] as Teams

                return !teams.state || !teams.isInYourTeam(entity)
            }

            return EntityUtils.targetMobs && EntityUtils.isMob(entity) || EntityUtils.targetAnimals &&
                    EntityUtils.isAnimal(entity)
        }

        return false
    }
    private fun isAlive(entity: EntityLivingBase) = entity.isEntityAlive && entity.health > 0

    fun isVisible(vec3: Vec3?): Boolean {
        val eyesPos = Vec3(
            mc.thePlayer.posX,
            mc.thePlayer.entityBoundingBox.minY + mc.thePlayer.getEyeHeight(),
            mc.thePlayer.posZ
        )
        return mc.theWorld.rayTraceBlocks(eyesPos, vec3) == null
    }

    private val canBlock: Boolean
        get() = mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item is ItemSword

    private fun startBlocking(interactEntity: Entity, interact: Boolean) {

        if (!(blockRate > 0 && RandomUtils.nextInt(0,100) <= blockRate)) return

        PacketUtils.sendPacketNoEvent(C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()))
        blockingStatus = true

        if (interact) {
            val positionEye = mc.renderViewEntity?.getPositionEyes(1F)

            val expandSize = interactEntity.collisionBorderSize.toDouble()
            val boundingBox = interactEntity.entityBoundingBox.expand(expandSize, expandSize, expandSize)

            val (yaw, pitch) = RotationUtils.targetRotation ?: Rotation(mc.thePlayer!!.rotationYaw, mc.thePlayer!!.rotationPitch)
            val yawCos = cos(-yaw * 0.017453292F - Math.PI.toFloat())
            val yawSin = sin(-yaw * 0.017453292F - Math.PI.toFloat())
            val pitchCos = -cos(-pitch * 0.017453292F)
            val pitchSin = sin(-pitch * 0.017453292F)
            val range = min(range.get().toDouble(), mc.thePlayer!!.getDistanceToEntityBox(interactEntity)) + 1
            val lookAt = positionEye!!.addVector(yawSin * pitchCos * range, pitchSin * range, yawCos * pitchCos * range)

            val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: return
            val hitVec = movingObject.hitVec

            mc.netHandler.addToSendQueue(C02PacketUseEntity(interactEntity, Vec3(hitVec.xCoord - interactEntity.posX, hitVec.yCoord - interactEntity.posY, hitVec.zCoord - interactEntity.posZ)))
            mc.netHandler.addToSendQueue(C02PacketUseEntity(interactEntity, Action.INTERACT))
        }
    }

    /**
     * Stop blocking
     */
    private fun stopBlocking() {
        if (blockingStatus) {
            PacketUtils.sendPacketNoEvent(
                C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN,
                    EnumFacing.DOWN
                )
            )
            blockingStatus = false
        }
    }
    private val cancelRun: Boolean
        get() = mc.thePlayer.isSpectator || !isAlive(mc.thePlayer)
                || (noBlink && LiquidBounce.moduleManager[Blink::class.java]!!.state && autoBlockMode != "HypixelBlink") || LiquidBounce.moduleManager[FreeCam::class.java]!!.state ||
                (noScaff && (LiquidBounce.moduleManager[Scaffold::class.java]!!.state)) || noInvAttack && mc.currentScreen is GuiContainer

    object CombatListener : Listenable {
        private var syncEntity: EntityLivingBase? = null
        private var totalPlayed = 0
        private var startTime = System.currentTimeMillis()
        var win = 0
        var killCounts = 0

        @EventTarget
        private fun onAttack(event: AttackEvent) {
            syncEntity = event.targetEntity as EntityLivingBase?
        }

        @EventTarget
        private fun onUpdate(event: UpdateEvent) {
            if (syncEntity != null && syncEntity!!.isDead) {
                ++killCounts
                syncEntity = null
            }
        }

        @EventTarget(ignoreCondition = true)
        private fun onPacket(event: PacketEvent) {
            val packet = event.packet
            if (event.packet is C00Handshake) startTime = System.currentTimeMillis()

            if (packet is S45PacketTitle) {
                val title = packet.message.formattedText
                if (title.contains("Winner")) {
                    win++
                }
                if (title.contains("BedWar")) {
                    totalPlayed++
                }
                if (title.contains("SkyWar")) {
                    totalPlayed++
                }
            }
        }

        override fun handleEvents() = true

        init {
            LiquidBounce.eventManager.registerListener(this)
        }
    }
    override val tag: String
        get() = rotationMode.get()
}
