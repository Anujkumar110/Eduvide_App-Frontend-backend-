package com.example.tutorapp.network

/**
 * Public slot APIs use teacher **userId** (`TeacherProfile.userId`); wallet/pay often use the same.
 */
fun TeacherItem.idForSlotApis(): String = userId.ifBlank { id }

/**
 * Wallet / pay endpoints often expect the teacher's auth user id.
 */
fun TeacherItem.idForWalletPay(): String = userId.ifBlank { id }
