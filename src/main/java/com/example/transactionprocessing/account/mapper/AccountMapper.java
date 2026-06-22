package com.example.transactionprocessing.account.mapper;

import com.example.transactionprocessing.account.dto.AccountResponse;
import com.example.transactionprocessing.account.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);
}
