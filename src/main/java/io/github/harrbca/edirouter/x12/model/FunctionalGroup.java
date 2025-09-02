package io.github.harrbca.edirouter.x12.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionalGroup {
    private GS gs;
    @Builder.Default
    private List<TransactionSet> transactionSets = new ArrayList<>();
}
