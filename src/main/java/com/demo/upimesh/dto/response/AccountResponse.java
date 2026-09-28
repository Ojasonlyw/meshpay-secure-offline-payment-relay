package com.demo.upimesh.dto.response;

import java.math.BigDecimal;

public record AccountResponse(String vpa, String holderName, BigDecimal balance, Long version) {}
