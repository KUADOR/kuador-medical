/// Option of a coded catalog: the API code and its Spanish label.
class CatalogOption {
  const CatalogOption(this.code, this.label);

  final String code;
  final String label;
}

/// Provisional catalogs of the self-registration form.
///
/// The document types, their validation and the sex values are [POR DEFINIR]
/// in the glossary (plan of MEDK-12); the api accepts the codes of a
/// versioned, editable catalog. Replace these lists, or load them from the api
/// when it exposes the catalog, once they are defined.
abstract final class RegistrationCatalog {
  static const List<CatalogOption> documentTypes = [
    CatalogOption('NATIONAL_ID', 'Cédula de identidad'),
    CatalogOption('PASSPORT', 'Pasaporte'),
  ];

  static const List<CatalogOption> sexes = [
    CatalogOption('FEMALE', 'Femenino'),
    CatalogOption('MALE', 'Masculino'),
  ];

  /// Age of majority fixed by the spec; only adults self-register.
  static const int adultAge = 18;
}

/// Age in whole years on [today].
int ageOn(DateTime birthDate, DateTime today) {
  var age = today.year - birthDate.year;
  final hadBirthday =
      today.month > birthDate.month ||
      (today.month == birthDate.month && today.day >= birthDate.day);
  if (!hadBirthday) age--;
  return age;
}
