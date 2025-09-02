package io.github.harrbca.edirouter.x12.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ISA {
    // ISA01..ISA16 as descriptive names
    private String authorizationInformationQualifier; // ISA01
    private String authorizationInformation;          // ISA02
    private String securityInformationQualifier;      // ISA03
    private String securityInformation;               // ISA04
    private String interchangeIdQualifierSender;      // ISA05
    private String interchangeSenderId;               // ISA06
    private String interchangeIdQualifierReceiver;    // ISA07
    private String interchangeReceiverId;             // ISA08
    private String interchangeDateYYMMDD;             // ISA09
    private String interchangeTimeHHMM;               // ISA10
    private String repetitionSeparatorChar;           // ISA11 (single char stored as String)
    private String interchangeControlVersion;         // ISA12
    private String interchangeControlNumber;          // ISA13
    private String acknowledgmentRequested;           // ISA14
    private String usageIndicator;                    // ISA15 (T/P)
    private String componentElementSeparatorChar;     // ISA16 (single char stored as String)

    // Detected delimiters (convenience)
    private char elementSeparator;
    private char segmentTerminator;
    private char repetitionSeparator;
    private char componentSeparator;
}
