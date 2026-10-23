package com.henheang.securityapi.utils;

import java.util.regex.Pattern;

public class PhoneNumberUtil {

    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern US_PATTERN = Pattern.compile("^\\+1[2-9]\\d{2}[2-9]\\d{2}\\d{4}$");
    private static final Pattern CAMBODIA_PATTERN = Pattern.compile("^\\+855[1-9]\\d{7,8}$");
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    /*
     * Checks if the phone number is in US format
     * @param phoneNumber the phone number to check
     * @return true if the phone number is in US format, false otherwise
     */

    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        String normalizedNumber = phoneNumber.trim();

        if (normalizedNumber.startsWith("+")) {
            return INTERNATIONAL_PATTERN.matcher(normalizedNumber).matches()
                    || US_PATTERN.matcher(normalizedNumber).matches()
                    || CAMBODIA_PATTERN.matcher(normalizedNumber).matches();
        }
        return SIMPLE_PATTERN.matcher(normalizedNumber).matches();
    }

    /*
     * Checks if the phone number is in US format
     * @param phoneNumber the phone number to check
     * @return true if the phone number is in US format, false otherwise
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String cleaned = phoneNumber.trim().replaceAll("[^+\\d]", "");

        if (cleaned.startsWith("+")) {
            cleaned = cleaned.replaceFirst("^\\+855", "+855");
        } else if (cleaned.startsWith("0")) {
            cleaned = "+855" + cleaned.substring(1);
        } else if (!cleaned.startsWith("+855")) {
            cleaned = "+855" + cleaned;
        }

        return cleaned;
    }

    /*
     *Formats number for display
     * @param phoneNumber the phone number to format
     * @return formatted phone number for display
     */

    public static String formatPhoneNumber(String phoneNumber) {
        if (isValidPhoneNumber(phoneNumber)) {
            return phoneNumber;
        }

        String normalizedNumber = normalizePhoneNumber(phoneNumber);

        if (normalizedNumber.startsWith("+1") && normalizedNumber.length() == 12) {
            return String.format(
                    "+1 (%s) %s-%s",
                    normalizedNumber.substring(2, 5),
                    normalizedNumber.substring(5, 8),
                    normalizedNumber.substring(8));
        }
        if (normalizedNumber.startsWith("+855")) {

            //        Cambodia phone number formatting
            if (normalizedNumber.length() == 13) {
                return String.format(
                        "+855 %s-%s",
                        normalizedNumber.substring(4, 7), normalizedNumber.substring(7));
            } else if (normalizedNumber.length() == 14) {
                return String.format(
                        "+855 %s-%s-%s",
                        normalizedNumber.substring(4, 7),
                        normalizedNumber.substring(7, 10),
                        normalizedNumber.substring(10));
            }
        }

        if (normalizedNumber.startsWith("+") && normalizedNumber.length() > 10) {
            return String.format(
                    "%s %s-%s",
                    normalizedNumber.substring(0, 3),
                    normalizedNumber.substring(3, 6),
                    normalizedNumber.substring(6));
        }

        return normalizedNumber;
    }

    /*
    check if the identifier phone number (contain only a digit and +) is valid
     */

    public static boolean isPhoneNumber(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }

        String cleaned = identifier.trim();
        return cleaned.matches("^[+]?[0-9\\s\\-()]+$");
    }

    /** Checks if the identifier is an email (contains @) */
    public static boolean isEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }
}
