package com.beefit.api.service;

import com.beefit.api.domain.User;
import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.exception.UserNotFoundException;
import com.beefit.api.mapper.WorkoutTemplateResponseMapper;
import com.beefit.api.repository.UserRepository;
import com.beefit.api.repository.WorkoutTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class WorkoutTemplateService {

    private final WorkoutTemplateRepository WTRepository;
    private final UserRepository userRepository;
    private final WorkoutTemplateResponseMapper workoutTemplateResponseMapper;

    @Transactional
    //Lembrar de implementar o id do amigo na parte do social
    public WorkoutTemplateResponseDTO createTemplate(WorkoutTemplateRequestDTO requestDTO, UUID userID /*, UUID friendID*/){
        WorkoutTemplate template = new WorkoutTemplate();
        template.setName(requestDTO.name());
        template.setMuscleGroupsList(requestDTO.muscleGroups());
        template.setExerciseList(requestDTO.exercises());

        User user = userRepository.findById(userID)
                .orElseThrow(UserNotFoundException::new);

        /*
        if(friendID !== null){
            User friend = userRepository.findById(userID).orElse(null);
        }
        */

        template.setUser(user);

        WorkoutTemplate savedTemplate = WTRepository.save(template);

        return new WorkoutTemplateResponseDTO(
                savedTemplate.getId(),
                savedTemplate.getName(),
                savedTemplate.getMuscleGroupsList(),
                savedTemplate.getExerciseList()
        );
    }

    @Transactional
    public List<WorkoutTemplateResponseDTO> getUserTemplates(UUID userID){

        List<WorkoutTemplate> templates = WTRepository.findByUserId(userID);

        return templates.stream().map(workoutTemplateResponseMapper::toDto).toList();
    }


}
