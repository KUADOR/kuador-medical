import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { authInterceptor } from '../../../core/session/auth.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import {
  LOGIN,
  MAIN_BRANCH,
  MULTI_BRANCH_ACCOUNT,
  NORTH_BRANCH,
  problem,
} from '../../../../testing/fixtures';
import { BranchSwitcher } from './branch-switcher';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('BranchSwitcher', () => {
  let backend: HttpTestingController;
  let session: SessionStore;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    session.start(LOGIN);
    const loading = session.ensureAccount();
    backend.expectOne('/api/v1/me').flush(MULTI_BRANCH_ACCOUNT);
    await loading;
  });

  afterEach(() => backend.verify());

  async function render() {
    const fixture = TestBed.createComponent(BranchSwitcher);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  function choose(root: HTMLElement, branchId: string) {
    const select = root.querySelector<HTMLSelectElement>('#branch-switcher')!;
    select.value = branchId;
    select.dispatchEvent(new Event('change'));
  }

  it('offers the assigned branches with a visible label', async () => {
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('label[for="branch-switcher"]')?.textContent).toContain('Sucursal');
    const options = Array.from(root.querySelectorAll('option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Main branch', 'North branch']);
    expect(root.querySelector<HTMLSelectElement>('#branch-switcher')?.value).toBe(MAIN_BRANCH.id);
  });

  it('switches the branch, reloads GET /me and goes to the landing page', async () => {
    const fixture = await render();
    choose(fixture.nativeElement, NORTH_BRANCH.id);

    const request = backend.expectOne('/api/v1/auth/switch-branch');
    expect(request.request.body).toEqual({
      branchId: NORTH_BRANCH.id,
      refreshToken: 'test-refresh',
    });
    request.flush({ ...LOGIN, accessToken: 'north-token', branch: NORTH_BRANCH });
    await tick();
    backend
      .expectOne('/api/v1/me')
      .flush({ ...MULTI_BRANCH_ACCOUNT, activeBranch: NORTH_BRANCH, permissions: [] });
    await tick();

    expect(session.account()?.activeBranch).toEqual(NORTH_BRANCH);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/status');
  });

  it('keeps the current branch and explains a rejected switch in Spanish', async () => {
    const fixture = await render();
    choose(fixture.nativeElement, NORTH_BRANCH.id);
    const { body, init } = problem(403, 'BRANCH_NOT_ASSIGNED');
    backend.expectOne('/api/v1/auth/switch-branch').flush(body, init);
    await tick();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      ERROR_MESSAGES.BRANCH_NOT_ASSIGNED,
    );
    expect(root.querySelector<HTMLSelectElement>('#branch-switcher')?.value).toBe(MAIN_BRANCH.id);
    expect(session.accessToken()).toBe('test-token');
  });
});
