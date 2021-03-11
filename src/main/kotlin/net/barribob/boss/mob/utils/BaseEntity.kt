package net.barribob.boss.mob.utils

import net.barribob.boss.mob.damage.IDamageHandler
import net.barribob.maelstrom.general.event.EventScheduler
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.boss.ServerBossBar
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.jetbrains.annotations.Nullable
import software.bernie.geckolib3.core.IAnimatable
import software.bernie.geckolib3.core.manager.AnimationFactory

abstract class BaseEntity(entityType: EntityType<out PathAwareEntity>, world: World) :
    PathAwareEntity(entityType, world), IAnimatable {
    private val animationFactory: AnimationFactory by lazy { AnimationFactory(this) }
    override fun getFactory(): AnimationFactory = animationFactory
    var idlePosition: Vec3d = Vec3d.ZERO // TODO: I don't actually know if this implementation works
    protected open val bossBar: ServerBossBar? = null
    protected open val damageHandler: IDamageHandler? = null
    protected open val statusHandler: IStatusHandler? = null
    protected val preTickEvents = EventScheduler()
    protected val postTickEvents = EventScheduler()

    final override fun tick() {
        preTickEvents.updateEvents()
        if (idlePosition == Vec3d.ZERO) idlePosition = pos
        if (world.isClient) {
            clientTick()
        } else {
            serverTick(world as ServerWorld)
        }
        super.tick()
        postTickEvents.updateEvents()
    }

    open fun clientTick() {} // Todo: this may not be the best pattern to use
    open fun serverTick(serverWorld: ServerWorld) {}

    override fun mobTick() {
        super.mobTick()
        bossBar?.percent = this.health / this.maxHealth
    }

    override fun fromTag(tag: CompoundTag) {
        super.fromTag(tag)
        if (hasCustomName()) {
            bossBar?.name = this.displayName
        }
    }

    final override fun setCustomName(@Nullable name: Text?) {
        super.setCustomName(name)
        bossBar?.name = this.displayName
    }

    override fun onStartedTrackingBy(player: ServerPlayerEntity?) {
        super.onStartedTrackingBy(player)
        bossBar?.addPlayer(player)
    }

    override fun onStoppedTrackingBy(player: ServerPlayerEntity?) {
        super.onStoppedTrackingBy(player)
        bossBar?.removePlayer(player)
    }

    final override fun readCustomDataFromTag(tag: CompoundTag?) {
        super.readCustomDataFromTag(tag)
    }

    // Todo: make final
    override fun handleStatus(status: Byte) {
        statusHandler?.handleClientStatus(status)
        super.handleStatus(status)
    }

    final override fun damage(source: DamageSource, amount: Float): Boolean {
        val stats = EntityStats(this)
        val handler = damageHandler
        if (!world.isClient) {
            handler?.beforeDamage(stats, source, amount)
        }
        val result = if (handler != null) {
            handler.shouldDamage(this, source, amount) && super.damage(source, amount)
        } else {
            super.damage(source, amount)
        }
        if (!world.isClient) {
            handler?.afterDamage(stats, source, amount)
        }
        return result
    }

    final override fun setTarget(target: LivingEntity?) {
        if (target == null) idlePosition = pos
        super.setTarget(target)
    }
}