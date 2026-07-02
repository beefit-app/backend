package com.beefit.api.mapper;

import com.beefit.api.domain.User;
import com.beefit.api.dto.auth.RegisterDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegisterMapper {

        @Mapping(source = "password", target = "passwordHash")
        User toEntity(RegisterDTO dto);

        @Mapping(target = "deviceId", ignore = true)
        @Mapping(target = "password", source = "passwordHash")
        RegisterDTO toDto(User user);
}
