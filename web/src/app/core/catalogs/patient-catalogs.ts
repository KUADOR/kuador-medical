// Catalogs of the patient data used by the web forms.
//
// [POR DEFINIR: tipos de documento, valores de sexo y texto del consentimiento LOPDP, pendientes
// en el Glosario (plan de MEDK-12)]. Until they are defined, the web only offers the codes the
// contract uses in its examples; the backend validates against its own versioned catalog.

export interface CatalogOption {
  code: string;
  label: string;
}

export const DOCUMENT_TYPES: readonly CatalogOption[] = [
  { code: 'NATIONAL_ID', label: 'Documento de identidad' },
];

export const SEX_OPTIONS: readonly CatalogOption[] = [
  { code: 'FEMALE', label: 'Femenino' },
  { code: 'MALE', label: 'Masculino' },
];

/** Version of the consent text shown to the person; sent with the registration. */
export const CONSENT_TEXT_VERSION = '2026-01';
