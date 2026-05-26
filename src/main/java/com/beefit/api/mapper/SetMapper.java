package com.beefit.api.mapper;

import org.mapstruct.Mapper;

import com.beefit.api.domain.Set;
import com.beefit.api.dto.SetResponseDTO;

@Mapper(componentModel = "spring")
public interface SetMapper {

    SetResponseDTO toDto(Set set);
}
