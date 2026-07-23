import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, getStoredToken } from '../api/client';
import { LoginPage } from './LoginPage';

describe('LoginPage temporary login', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('opens a temporary session without invoking the OTP mail flow or storing a token', async () => {
    const sendOtp = vi.spyOn(api, 'sendOtp');
    const verifyOtp = vi.spyOn(api, 'verifyOtp');
    const onLoginSuccess = vi.fn();
    const user = userEvent.setup();

    render(<LoginPage onLoginSuccess={onLoginSuccess} />);
    await user.click(screen.getByRole('button', { name: 'Geçici Giriş' }));

    expect(onLoginSuccess).toHaveBeenCalledWith('temporary@voltflow.local');
    expect(sendOtp).not.toHaveBeenCalled();
    expect(verifyOtp).not.toHaveBeenCalled();
    expect(getStoredToken()).toBeNull();
  });
});
