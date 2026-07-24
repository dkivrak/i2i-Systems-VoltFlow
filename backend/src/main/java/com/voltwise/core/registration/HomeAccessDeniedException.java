package com.voltwise.core.registration;

public class HomeAccessDeniedException extends RuntimeException {
    public HomeAccessDeniedException() {
        super("Bu ev üzerinde işlem yapma yetkiniz yok.");
    }
}
