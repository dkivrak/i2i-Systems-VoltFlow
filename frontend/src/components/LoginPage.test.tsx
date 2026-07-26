import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, setStoredToken } from '../api/client';
import { LoginPage } from './LoginPage';

const authenticatedResponse = {
  token: 'header.payload.signature',
  user: { id: 7, email: 'owner@example.com' },
  message: 'Giriş başarılı.',
};

describe('LoginPage email and password authentication', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders only the real password login without social or fake reset controls', () => {
    render(<LoginPage onLoginSuccess={vi.fn()} />);

    expect(
      screen.getByRole('textbox', { name: 'E-posta adresi' }),
    ).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText('Şifre')).toHaveAttribute(
      'autocomplete',
      'current-password',
    );
    expect(
      screen.getAllByRole('button', { name: 'Giriş yap' }),
    ).toHaveLength(2);
    expect(
      screen.queryByRole('button', {
        name: /google|facebook|apple|sosyal|şifremi unuttum/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('normalizes email, submits credentials, and completes login', async () => {
    const login = vi.spyOn(api, 'login').mockImplementation(async () => {
      setStoredToken(authenticatedResponse.token);
      return authenticatedResponse;
    });
    const onLoginSuccess = vi.fn();
    const user = userEvent.setup();

    render(<LoginPage onLoginSuccess={onLoginSuccess} />);
    await user.type(
      screen.getByRole('textbox', { name: 'E-posta adresi' }),
      'OWNER@EXAMPLE.COM',
    );
    await user.type(screen.getByLabelText('Şifre'), 'securePassword');
    await user.click(
      screen.getAllByRole('button', { name: 'Giriş yap' })[1],
    );

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith(
        'owner@example.com',
        'securePassword',
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() =>
      expect(onLoginSuccess).toHaveBeenCalledWith('owner@example.com'),
    );
  });

  it('shows a generic invalid-credentials error and preserves the email', async () => {
    vi.spyOn(api, 'login').mockRejectedValue(
      new ApiError('E-posta adresi veya şifre hatalı.', 401),
    );
    const user = userEvent.setup();

    render(<LoginPage onLoginSuccess={vi.fn()} />);
    const email = screen.getByRole('textbox', { name: 'E-posta adresi' });
    await user.type(email, 'owner@example.com');
    await user.type(screen.getByLabelText('Şifre'), 'wrongPassword');
    await user.click(
      screen.getAllByRole('button', { name: 'Giriş yap' })[1],
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'E-posta adresi veya şifre hatalı.',
    );
    expect(email).toHaveValue('owner@example.com');
  });

  it('shows password controls and respects the privacy-oriented hidden state', async () => {
    const user = userEvent.setup();
    render(<LoginPage onLoginSuccess={vi.fn()} />);

    const password = screen.getByLabelText('Şifre');
    expect(password).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Şifreyi göster' }));
    expect(password).toHaveAttribute('type', 'text');
    expect(
      screen.getByRole('button', { name: 'Şifreyi gizle' }),
    ).toHaveAttribute('aria-pressed', 'true');
  });

  it('renders registration fields and blocks mismatched passwords', async () => {
    const register = vi.spyOn(api, 'register');
    const user = userEvent.setup();

    render(
      <LoginPage initialMode="SIGNUP" onLoginSuccess={vi.fn()} />,
    );

    expect(screen.getByLabelText('Şifre')).toHaveAttribute(
      'autocomplete',
      'new-password',
    );
    expect(screen.getByLabelText('Şifre tekrarı')).toHaveAttribute(
      'autocomplete',
      'new-password',
    );
    expect(screen.getByText('Şifreniz en az 8 karakter olmalıdır.')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', { name: 'E-posta adresi' }),
      'owner@example.com',
    );
    await user.type(screen.getByLabelText('Şifre'), 'securePassword');
    await user.type(
      screen.getByLabelText('Şifre tekrarı'),
      'differentPassword',
    );
    await user.click(screen.getByRole('button', { name: 'Hesap oluştur' }));

    expect(await screen.findByText('Şifreler eşleşmiyor.')).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it('registers a real account and completes the authenticated transition', async () => {
    const response = {
      ...authenticatedResponse,
      message: 'Hesabınız oluşturuldu.',
    };
    const register = vi.spyOn(api, 'register').mockImplementation(async () => {
      setStoredToken(response.token);
      return response;
    });
    const onLoginSuccess = vi.fn();
    const user = userEvent.setup();

    render(
      <LoginPage initialMode="SIGNUP" onLoginSuccess={onLoginSuccess} />,
    );
    await user.type(
      screen.getByRole('textbox', { name: 'E-posta adresi' }),
      'owner@example.com',
    );
    await user.type(screen.getByLabelText('Şifre'), 'securePassword');
    await user.type(
      screen.getByLabelText('Şifre tekrarı'),
      'securePassword',
    );
    await user.click(screen.getByRole('button', { name: 'Hesap oluştur' }));

    await waitFor(() =>
      expect(register).toHaveBeenCalledWith(
        'owner@example.com',
        'securePassword',
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() =>
      expect(onLoginSuccess).toHaveBeenCalledWith('owner@example.com'),
    );
  });

  it('executes quick demo login for voltflow@gmail.com when demo button is clicked', async () => {
    const demoResponse = {
      token: 'header.payload.signature',
      user: { id: 1, email: 'voltflow@gmail.com' },
      message: 'Giriş başarılı.',
    };
    const login = vi.spyOn(api, 'login').mockImplementation(async () => demoResponse);
    const onLoginSuccess = vi.fn();
    const user = userEvent.setup();

    render(<LoginPage onLoginSuccess={onLoginSuccess} />);

    const demoButton = screen.getByRole('button', {
      name: /Hızlı Demo Girişi/i,
    });
    await user.click(demoButton);

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith(
        'voltflow@gmail.com',
        'VoltFlow123!',
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() =>
      expect(onLoginSuccess).toHaveBeenCalledWith('voltflow@gmail.com'),
    );
  });
});
