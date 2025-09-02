package io.github.harrbca.edirouter.x12.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSet {
    private String transactionSetIdentifierCode; // ST01 (e.g., 850/856/832/855/810/997)
    private String transactionSetControlNumber;  // ST02
    private int indexInInterchange;
    private int indexInGroup;
}
