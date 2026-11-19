import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthorizeLinkRequest,
  Consent,
  CreateGuardianLinkRequest,
  GuardianLink,
  Patient,
  PatientInvitationRequest,
  PatientPage,
  PatientPaths,
  RecordConsentRequest,
  RegisterPatientRequest,
  RegistrationSettings,
  UpdatePatientRequest,
  UpdateRegistrationSettingsRequest,
  endGuardianLinkPath,
  patientPath,
  patientSubPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

export interface PatientQuery {
  q?: string;
  page?: number;
  size?: number;
}

/**
 * Patients of the clinic resolved from the token (ADR-007): no request carries a tenant id.
 * Creations send an `Idempotency-Key` header and a client-generated `id`, so retrying after a
 * network error never registers the same patient, consent or link twice.
 */
@Injectable({ providedIn: 'root' })
export class PatientsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /patients (patients.read). */
  list(query: PatientQuery = {}): Observable<PatientPage> {
    let params = new HttpParams();
    if (query.q?.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.page !== undefined) {
      params = params.set('page', query.page);
    }
    if (query.size !== undefined) {
      params = params.set('size', query.size);
    }
    return this.http.get<PatientPage>(this.url(PatientPaths.patients), { params });
  }

  /** GET /patients/{id} (patients.read). */
  get(id: string): Observable<Patient> {
    return this.http.get<Patient>(this.url(patientPath(id)));
  }

  /** POST /patients (patients.register). */
  register(request: RegisterPatientRequest, idempotencyKey: string): Observable<Patient> {
    return this.http.post<Patient>(this.url(PatientPaths.patients), request, {
      headers: idempotency(idempotencyKey),
    });
  }

  /** PATCH /patients/{id} (patients.edit), with the version read for optimistic locking. */
  update(id: string, request: UpdatePatientRequest): Observable<Patient> {
    return this.http.patch<Patient>(this.url(patientPath(id)), request);
  }

  /** POST /patients/{id}/consents (patients.register). */
  recordConsent(
    id: string,
    request: RecordConsentRequest,
    idempotencyKey: string,
  ): Observable<Consent> {
    return this.http.post<Consent>(this.url(patientSubPath(id, 'consents')), request, {
      headers: idempotency(idempotencyKey),
    });
  }

  /** GET /patients/{id}/guardian-links (patients.read): current and historical links. */
  guardianLinks(id: string): Observable<GuardianLink[]> {
    return this.http.get<GuardianLink[]>(this.url(patientSubPath(id, 'guardian-links')));
  }

  /** POST /patients/{id}/guardian-links (patients.guardians.manage): legal guardian of a minor. */
  addGuardianLink(
    id: string,
    request: CreateGuardianLinkRequest,
    idempotencyKey: string,
  ): Observable<GuardianLink> {
    return this.http.post<GuardianLink>(this.url(patientSubPath(id, 'guardian-links')), request, {
      headers: idempotency(idempotencyKey),
    });
  }

  /** POST /patients/{id}/guardian-links/{linkId}/end (patients.guardians.manage). */
  endGuardianLink(id: string, linkId: string): Observable<GuardianLink> {
    return this.http.post<GuardianLink>(this.url(endGuardianLinkPath(id, linkId)), null);
  }

  /** POST /patients/{id}/account-invitation (patients.register): adults only. */
  inviteAccount(id: string, request: PatientInvitationRequest): Observable<void> {
    return this.http.post<void>(this.url(patientSubPath(id, 'account-invitation')), request);
  }

  /** GET /me/guardian-links (PATIENT accounts). */
  myGuardianLinks(): Observable<GuardianLink[]> {
    return this.http.get<GuardianLink[]>(this.url(PatientPaths.myGuardianLinks));
  }

  /** POST /me/guardian-links (adult PATIENT accounts): emergency contact or delegated access. */
  authorizeMyGuardianLink(
    request: AuthorizeLinkRequest,
    idempotencyKey: string,
  ): Observable<GuardianLink> {
    return this.http.post<GuardianLink>(this.url(PatientPaths.myGuardianLinks), request, {
      headers: idempotency(idempotencyKey),
    });
  }

  /** GET /patient-registration-settings (patients.read). */
  registrationSettings(): Observable<RegistrationSettings> {
    return this.http.get<RegistrationSettings>(this.url(PatientPaths.registrationSettings));
  }

  /** PUT /patient-registration-settings (patients.selfRegistration.configure). */
  updateRegistrationSettings(
    request: UpdateRegistrationSettingsRequest,
  ): Observable<RegistrationSettings> {
    return this.http.put<RegistrationSettings>(
      this.url(PatientPaths.registrationSettings),
      request,
    );
  }

  private url(path: string): string {
    return this.config.apiUrl(path);
  }
}

function idempotency(key: string): Record<string, string> {
  return { 'Idempotency-Key': key };
}

/**
 * Client id and Idempotency-Key of one creation. The page keeps the same attempt while it
 * retries after a network error and takes a new one once the server answered.
 */
export interface CreationAttempt {
  id: string;
  key: string;
}

export function newAttempt(): CreationAttempt {
  return { id: crypto.randomUUID(), key: crypto.randomUUID() };
}
