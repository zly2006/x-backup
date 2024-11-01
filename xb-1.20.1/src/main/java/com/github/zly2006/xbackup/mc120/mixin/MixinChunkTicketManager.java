package com.github.zly2006.xbackup.mc120.mixin;

import com.github.zly2006.xbackup.multi.RestoreAware;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.collection.SortedArraySet;
import net.minecraft.world.SimulationDistanceLevelPropagator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;

@Mixin(ChunkTicketManager.class)
public abstract class MixinChunkTicketManager implements RestoreAware {
    @Shadow @Final public Long2ObjectOpenHashMap<SortedArraySet<ChunkTicket<?>>> ticketsByPosition;

    @Shadow @Final public SimulationDistanceLevelPropagator simulationDistanceTracker;

    @Shadow @Final private ChunkTicketManager.TicketDistanceLevelPropagator distanceFromTicketTracker;

    @Override
    public void preRestore() {
        ImmutableSet<ChunkTicketType<?>> immutableSet = ImmutableSet.of(ChunkTicketType.LIGHT);
        ObjectIterator<Long2ObjectMap.Entry<SortedArraySet<ChunkTicket<?>>>> objectIterator = this.ticketsByPosition.long2ObjectEntrySet().fastIterator();

        while(objectIterator.hasNext()) {
            Long2ObjectMap.Entry<SortedArraySet<ChunkTicket<?>>> entry = objectIterator.next();
            Iterator<ChunkTicket<?>> iterator = entry.getValue().iterator();
            boolean bl = false;

            while(iterator.hasNext()) {
                ChunkTicket<?> chunkTicket = iterator.next();
                if (immutableSet.contains(chunkTicket.getType())) {
                    iterator.remove();
                    bl = true;
                    this.simulationDistanceTracker.remove(entry.getLongKey(), chunkTicket);
                }
            }

            if (bl) {
                this.distanceFromTicketTracker.updateLevel(entry.getLongKey(), ChunkLevels.INACCESSIBLE + 1, false);
            }

            if (entry.getValue().isEmpty()) {
                objectIterator.remove();
            }
        }

    }

    @Override
    public void postRestore() {

    }
}
