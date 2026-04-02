package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.DrawPlan;

import java.util.List;

public record DrawGroupSlice(
        ResourceGroupSlice resourceGroup,
        List<DrawPlan.DirectDrawItem> directItems
) {
    public DrawGroupSlice {
        directItems = directItems != null ? List.copyOf(directItems) : List.of();
    }
}
