package com.beefit.api.infra.database;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.dto.ExerciseSeedDTO;
import com.beefit.api.exception.DatabaseSeederFailException;
import com.beefit.api.repository.CatalogExerciseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class DatabaseSeeder implements CommandLineRunner { // Interface faz a classe rodar na inciciação

    private final CatalogExerciseRepository catalogExerciseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseSeeder(CatalogExerciseRepository exerciseRepository) {
        this.catalogExerciseRepository = exerciseRepository;
    }

    @Override
    // Função principal
    public void run(String... args) throws Exception {
        catalogExerciseFactory();
    }

    public void catalogExerciseFactory(){

        if(catalogExerciseRepository.count() == 0){
            System.out.println("Tabela de exercícios vazia. Iniciando processo de carga inicial...");

            // Mapeia para o arquivo json
            ClassPathResource resource = new ClassPathResource("seeds/exercises.json");

            try(InputStream inputStream = resource.getInputStream() /* Passa do Json para bytes */){
                // Cria lista de ExerciseSeedDTO a partir dos Json
                List<ExerciseSeedDTO> seeds = objectMapper.readValue(
                        inputStream,
                        new TypeReference<List<ExerciseSeedDTO>>() {}
                );

                // Gera lista vazia de exercícios
                List<CatalogExercise> exercises = new ArrayList<>();

                for(ExerciseSeedDTO seed : seeds){

                    CatalogExercise exercise = new CatalogExercise();
                    String fullName = seed.getName().substring(0,1).toUpperCase() + seed.getName().substring(1);
                    exercise.setName(fullName);
                    if (seed.getInstructions() != null && !seed.getInstructions().isEmpty()) {
                        // Junta todos os passos quebando a linha (\n) para ficar formatado
                        String fullDescription = String.join("\n", seed.getInstructions());

                        // Garante que nao vai ser maior que 1000 caracteres
                        if (fullDescription.length() > 1000) {
                            fullDescription = fullDescription.substring(0, 997) + "...";
                        }

                        exercise.setInstructions(fullDescription);
                    }

                    List<MuscleGroups> primaryGroups  = seed.getPrimaryMuscleGroupList().stream()
                            .map(MuscleGroups::fromString)
                            .toList();

                    List<MuscleGroups> secondaryGroups  = seed.getSecondaryMuscleGroupList().stream()
                            .map(MuscleGroups::fromString)
                            .toList();

                    exercise.setPrimaryMuscleGroupsList(primaryGroups);
                    exercise.setSecondaryMuscleGroupsList(secondaryGroups);

                    // Adiciona o exercício criado à lista
                    exercises.add(exercise);
                }

                // Salva todos exercícios de uma vez
                catalogExerciseRepository.saveAll(exercises);

                System.out.println("Carregamento concluído com sucesso!");

            } catch (IOException | IllegalArgumentException e){
                throw new DatabaseSeederFailException("Não foi possível carregar os dados dos exercícios para o banco de dados.", e);
            }
        }
        else{
            System.out.println("Iniciando aplicação...");
        }
    }
}
