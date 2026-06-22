package com.example.transactionprocessing.transaction.mapper;

import com.example.transactionprocessing.transaction.dto.response.TransactionResponse;
import com.example.transactionprocessing.transaction.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponse toResponse(Transaction transaction);
}
