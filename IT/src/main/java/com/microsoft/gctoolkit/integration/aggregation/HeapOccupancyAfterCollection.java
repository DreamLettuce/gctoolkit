package com.microsoft.gctoolkit.integration.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregates;
import com.microsoft.gctoolkit.aggregator.Aggregator;
import com.microsoft.gctoolkit.aggregator.EventSource;
import com.microsoft.gctoolkit.event.g1gc.G1GCPauseEvent;
import com.microsoft.gctoolkit.event.generational.GenerationalGCPauseEvent;
import com.microsoft.gctoolkit.event.shenandoah.ShenandoahCycle;
import com.microsoft.gctoolkit.event.zgc.ZGCCycle;

@Aggregates({EventSource.G1GC,EventSource.GENERATIONAL,EventSource.ZGC,EventSource.SHENANDOAH})
public class HeapOccupancyAfterCollection extends Aggregator<HeapOccupancyAfterCollectionAggregation> {

    public HeapOccupancyAfterCollection(HeapOccupancyAfterCollectionAggregation results) {
        super(results);
        register(GenerationalGCPauseEvent.class, this::extractHeapOccupancy);
        register(G1GCPauseEvent.class, this::extractHeapOccupancy);
        register(ZGCCycle.class,this::extractHeapOccupancy);
        register(ShenandoahCycle.class,this::extractHeapOccupancy);
    }

    private void extractHeapOccupancy(GenerationalGCPauseEvent event) {
        aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), event.getHeap().getOccupancyAfterCollection());
    }

    private void extractHeapOccupancy(G1GCPauseEvent event) {
        aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), event.getHeap().getOccupancyAfterCollection());

    }

    private void extractHeapOccupancy(ZGCCycle event) {
        aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), event.getLive().getReclaimEnd());
    }

    private void extractHeapOccupancy(ShenandoahCycle event) {
        //aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), event.getOccupancy());
    }
}

