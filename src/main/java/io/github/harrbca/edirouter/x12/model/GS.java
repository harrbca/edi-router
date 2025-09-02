package io.github.harrbca.edirouter.x12.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GS {
    // GS01..GS08
    private String functionalIdentifierCode;   // GS01 (e.g., PO/SH/SC/PR/IN/FA)
    private String applicationSenderCode;      // GS02
    private String applicationReceiverCode;    // GS03
    private String groupDateCCYYMMDD;         // GS04
    private String groupTime;                  // GS05
    private String groupControlNumber;         // GS06
    private String responsibleAgencyCode;      // GS07 (X)
    private String versionReleaseIndustryCode; // GS08 (e.g., 007050)
}
