package io.github.harrbca.edirouter.x12;

import io.github.harrbca.edirouter.x12.model.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class X12EnvelopeService {
    public X12ParseResult parse(@NonNull String edi) {
        return parse(new ByteArrayInputStream(edi.getBytes(StandardCharsets.UTF_8)));
    }

    public X12ParseResult parse(@NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse EDI from file: " + file, e);
        }
    }

    public X12ParseResult parse(@NonNull Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse EDI from path: " + path, e);
        }
    }

    public X12ParseResult parse(@NonNull InputStream in) {
        String content;
        try {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read EDI content", e);
        }
        return parseInternal(content);
    }

    private X12ParseResult parseInternal(String content) {
        // normalize any unicode oddities, but keep raw delimiters
        String edi = content;

        // Find ISA segment start
        int isaIdx = edi.indexOf("ISA");
        if (isaIdx < 0) throw new IllegalArgumentException("No ISA segment found");

        // Element separator is the next character after "ISA"
        if (isaIdx + 3 >= edi.length()) {
            throw new IllegalArgumentException("Truncated after ISA");
        }
        char elementSep = edi.charAt(isaIdx + 3);

        // Parse ISA by walking forward and collecting 16 elements, then learn the segment terminator.
        List<String> isaFields = new ArrayList<>(16);
        int cursor = isaIdx + 4; // first char after "ISA" + element separator already consumed logically
        // The first field we append is "ISA" itself? In X12, 'ISA' is the tag, then 16 elements.
        // We want only the 16 elements (ISA01..ISA16).
        // So cursor starts at first char after the element separator following "ISA".
        // But the first element (ISA01) begins right at 'cursor', up to next elementSep etc.

        // Collect 16 elements, splitting on elementSep
        for (int i = 0; i < 16; i++) {
            int nextSep = edi.indexOf(elementSep, cursor);
            if (nextSep < 0) {
                // For the 16th element, it might be immediately followed by the segment terminator, not another elementSep.
                // So if not found and we're at the last element, read until we hit a likely segment terminator.
                if (i == 15) {
                    int segEnd = findSegmentEnd(edi, cursor);
                    isaFields.add(edi.substring(cursor, segEnd));
                    cursor = segEnd;
                    break;
                }
                throw new IllegalArgumentException("Could not locate ISA element separator for element " + (i + 1));
            }
            // Special case: the 16th element (ISA16) MUST NOT be terminated by elementSep; it’s followed by the segment terminator.
            if (i == 15) {
                // If we found another elementSep, that's malformed (some files include it though). Fall back to segment end.
                int segEnd = findSegmentEnd(edi, cursor);
                isaFields.add(edi.substring(cursor, segEnd));
                cursor = segEnd;
                break;
            }
            isaFields.add(edi.substring(cursor, nextSep));
            cursor = nextSep + 1;
        }

        if (isaFields.size() != 16) {
            throw new IllegalArgumentException("Invalid ISA: expected 16 elements, found " + isaFields.size());
        }

        // Segment terminator is the next char after ISA16
        if (cursor >= edi.length()) {
            throw new IllegalArgumentException("Unexpected end of file after ISA16");
        }
        char segmentTerm = edi.charAt(cursor);
        // Some files use CRLF as terminator; if so, segmentTerm likely '\r' and next is '\n'. We'll normalize below.

        // Repetition separator (ISA11) and component separator (ISA16)
        String isa11 = isaFields.get(10);
        String isa16 = isaFields.get(15);
        char repetitionSep = isa11 != null && !isa11.isEmpty() ? isa11.charAt(0) : '^';
        char componentSep  = isa16 != null && !isa16.isEmpty() ? isa16.charAt(0) : ':';

        ISA isa = ISA.builder()
                .authorizationInformationQualifier(isaFields.get(0))   // ISA01
                .authorizationInformation(isaFields.get(1))            // ISA02
                .securityInformationQualifier(isaFields.get(2))        // ISA03
                .securityInformation(isaFields.get(3))                 // ISA04
                .interchangeIdQualifierSender(isaFields.get(4))        // ISA05
                .interchangeSenderId(isaFields.get(5))                 // ISA06
                .interchangeIdQualifierReceiver(isaFields.get(6))      // ISA07
                .interchangeReceiverId(isaFields.get(7))               // ISA08
                .interchangeDateYYMMDD(isaFields.get(8))               // ISA09
                .interchangeTimeHHMM(isaFields.get(9))                 // ISA10
                .repetitionSeparatorChar(isaFields.get(10))            // ISA11 (as String)
                .interchangeControlVersion(isaFields.get(11))          // ISA12
                .interchangeControlNumber(isaFields.get(12))           // ISA13
                .acknowledgmentRequested(isaFields.get(13))            // ISA14
                .usageIndicator(isaFields.get(14))                     // ISA15
                .componentElementSeparatorChar(isaFields.get(15))      // ISA16 (as String)
                .elementSeparator(elementSep)
                .segmentTerminator(segmentTerm)
                .repetitionSeparator(repetitionSep)
                .componentSeparator(componentSep)
                .build();

        // Split all segments using the determined segment terminator
        // Handle CRLF-friendly split: treat "\r\n" or "\n" or "~" etc.
        List<String> segments = splitSegments(edi, segmentTerm);

        // Parse GS & ST in order, linked
        List<FunctionalGroup> groups = new ArrayList<>();
        FunctionalGroup currentGroup = null;
        int stGlobalIndex = 0;

        for (String seg : segments) {
            if (seg.isBlank()) continue;
            int tagEnd = seg.indexOf(elementSep);
            String tag = tagEnd < 0 ? seg : seg.substring(0, tagEnd);

            // safe split into elements (keeping empty ones)
            String[] parts = splitKeepEmpty(seg, elementSep);

            switch (tag) {
                case "GS": {
                    GS gs = GS.builder()
                            .functionalIdentifierCode(value(parts, 1))   // GS01
                            .applicationSenderCode(value(parts, 2))      // GS02
                            .applicationReceiverCode(value(parts, 3))    // GS03
                            .groupDateCCYYMMDD(value(parts, 4))          // GS04
                            .groupTime(value(parts, 5))                  // GS05
                            .groupControlNumber(value(parts, 6))         // GS06
                            .responsibleAgencyCode(value(parts, 7))      // GS07
                            .versionReleaseIndustryCode(value(parts, 8)) // GS08
                            .build();
                    currentGroup = FunctionalGroup.builder().gs(gs).build();
                    groups.add(currentGroup);
                    break;
                }
                case "ST": {
                    TransactionSet ts = TransactionSet.builder()
                            .transactionSetIdentifierCode(value(parts, 1)) // ST01
                            .transactionSetControlNumber(value(parts, 2))  // ST02
                            .indexInInterchange(stGlobalIndex++)
                            .indexInGroup(currentGroup == null ? -1 : currentGroup.getTransactionSets().size())
                            .build();
                    if (currentGroup == null) {
                        currentGroup = FunctionalGroup.builder().gs(GS.builder().build()).build();
                        groups.add(currentGroup);
                    }
                    currentGroup.getTransactionSets().add(ts);
                    break;
                }
                default:
                    // ignore all other segments for this envelope parser
                    break;
            }
        }

        return X12ParseResult.builder()
                .isa(isa)
                .functionalGroups(groups)
                .build();
    }

    // --- Helpers ---

    private static int findSegmentEnd(String edi, int fromIdx) {
        // Typical terminators: '~', '\n', '\r'
        // Find the first occurrence of any of them after fromIdx.
        int tilde = edi.indexOf('~', fromIdx);
        int lf = edi.indexOf('\n', fromIdx);
        int cr = edi.indexOf('\r', fromIdx);

        int min = Integer.MAX_VALUE;
        if (tilde >= 0) min = Math.min(min, tilde);
        if (lf >= 0) min = Math.min(min, lf);
        if (cr >= 0) min = Math.min(min, cr);

        if (min == Integer.MAX_VALUE) {
            // No obvious terminator; assume end of string
            return edi.length();
        }
        return min;
    }

    private static List<String> splitSegments(String edi, char segmentTerm) {
        // If terminator is '\r' and next is '\n', treat CRLF as one.
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < edi.length(); i++) {
            if (edi.charAt(i) == segmentTerm) {
                String seg = edi.substring(start, i).trim();
                out.add(seg);
                // If CRLF, skip the following '\n'
                if (segmentTerm == '\r' && i + 1 < edi.length() && edi.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        // add trailing tail if no final terminator
        if (start < edi.length()) {
            String tail = edi.substring(start).trim();
            if (!tail.isEmpty()) out.add(tail);
        }
        return out;
    }

    private static String[] splitKeepEmpty(String segment, char elementSep) {
        // Split without losing empty trailing elements
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < segment.length(); i++) {
            if (segment.charAt(i) == elementSep) {
                parts.add(segment.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(segment.substring(start));
        return parts.toArray(new String[0]);
    }

    private static String value(String[] parts, int index) {
        return index < parts.length ? parts[index] : null;
    }
}
