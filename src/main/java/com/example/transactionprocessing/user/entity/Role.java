package com.example.transactionprocessing.user.entity;

/**
 * Authorization roles. USER can create/view their own transactions and accounts;
 * ADMIN can additionally view all transactions and manually retry failed/dead-lettered ones.
 */
public enum Role {
    USER,
    ADMIN
}
