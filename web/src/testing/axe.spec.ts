import { axeViolations, expectNoAxeViolations } from './axe';

describe('axe-core test helper', () => {
  let host: HTMLElement;

  beforeEach(() => {
    host = document.createElement('main');
    document.body.appendChild(host);
  });

  afterEach(() => host.remove());

  it('reports WCAG A/AA violations with their rule and target', async () => {
    host.innerHTML = '<img src="x.png"><button></button><input type="text">';

    const violations = await axeViolations(host);

    expect(violations.map((v) => v.id).sort()).toEqual(['button-name', 'image-alt', 'label']);
    expect(violations.find((v) => v.id === 'image-alt')?.targets).toEqual(['img']);
  });

  it('passes on accessible markup', async () => {
    host.innerHTML = `
      <h1>Pacientes</h1>
      <label for="q">Buscar</label><input id="q" type="search">
      <button type="button">Buscar</button>
      <img src="x.png" alt="Logotipo de la clínica">
    `;

    await expectNoAxeViolations(host);
  });

  it('fails the test when there are violations', async () => {
    host.innerHTML = '<button></button>';

    await expect(expectNoAxeViolations(host)).rejects.toThrow(/button-name/);
  });
});
