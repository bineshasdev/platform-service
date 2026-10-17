package com.flowbinary.platformservice.model;

/**
 * Type of legal / licensing credential a tenant provides for verification.
 *
 * <p>The applicable set varies by {@link TenantType} and by country. Documents
 * that do not map to a known value use {@link #OTHER} with a free-text
 * description carried in {@code TenantCredential.attributes}.
 */
public enum CredentialType {

    /** Clinical establishment registration (e.g. India Clinical Establishments Act). */
    CLINICAL_ESTABLISHMENT_LICENSE,

    /** Individual practitioner council / board registration. */
    PRACTITIONER_REGISTRATION,

    /** Retail pharmacy drug licence (India Form 20). */
    DRUG_LICENSE_FORM_20,

    /** Wholesale pharmacy drug licence (India Form 21). */
    DRUG_LICENSE_FORM_21,

    /** Laboratory accreditation — NABL. */
    NABL_ACCREDITATION,

    /** Hospital / clinic accreditation — NABH. */
    NABH_ACCREDITATION,

    /** Pre-conception and Pre-natal Diagnostic Techniques Act registration. */
    PCPNDT_REGISTRATION,

    /** Bio-medical waste handling authorisation. */
    BIOMEDICAL_WASTE_AUTHORIZATION,

    /** Goods & Services Tax / VAT registration. */
    GST_REGISTRATION,

    /** Permanent Account Number / national tax identifier. */
    PAN,

    /** Local municipal trade licence. */
    TRADE_LICENSE,

    /** Food safety licence (stores selling nutraceuticals / food items). */
    FSSAI_LICENSE,

    /** Any other country- or type-specific document. */
    OTHER
}
