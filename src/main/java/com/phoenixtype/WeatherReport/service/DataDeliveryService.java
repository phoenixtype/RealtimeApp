package com.phoenixtype.WeatherReport.service;// src/main/java/com/phoenixtype/WeatherReport/service/DataDeliveryService.java

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenixtype.WeatherReport.model.WeatherData;
import com.phoenixtype.WeatherReport.repository.WeatherDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static com.phoenixtype.WeatherReport.constant.Constants.WEATHER_REPORT_DATA_BUCKET;
import static com.phoenixtype.WeatherReport.constant.Constants.WEATHER_REPORT_DATA_INCREMENTS_JSON;


@Service
@RequiredArgsConstructor
public class DataDeliveryService {

    private final WeatherDataRepository weatherDataRepository;
    private final DataIngestionService dataIngestionService;
    private final S3Client s3Client;

    private Long lastFetchedTimestamp;

    @PostConstruct
    public void init() {
        lastFetchedTimestamp = weatherDataRepository.findLastFetchedTimestamp();
        if (lastFetchedTimestamp == null) {
            lastFetchedTimestamp = 0L;
        }
    }

    public List<WeatherData> fetchDataIncrements() {
        List<WeatherData> dataIncrements = weatherDataRepository.findDataIncrements(lastFetchedTimestamp);
        if (!dataIncrements.isEmpty()) {
            lastFetchedTimestamp = Long.valueOf(dataIncrements.get(dataIncrements.size() - 1).getTimestamp());
            weatherDataRepository.updateLastFetchedTimestamp(lastFetchedTimestamp, dataIncrements.get(dataIncrements.size() - 1).getId());
        }
        return dataIncrements;
    }

    public void sendDataToS3Bucket(List<WeatherData> dataIncrements) {
        try {
            List<WeatherData> validDataIncrements = validateData(dataIncrements);
            if (validDataIncrements.size() < dataIncrements.size()) {
                Long lastValidTimestamp = validDataIncrements.isEmpty() ? lastFetchedTimestamp : Long.valueOf(validDataIncrements.get(validDataIncrements.size() - 1).getTimestamp());
                dataIngestionService.backfillDataGaps(lastValidTimestamp);
                validDataIncrements = fetchDataIncrements(); // Re-fetch data after backfilling
            }

            String bucketName = WEATHER_REPORT_DATA_BUCKET;
            String keyName = WEATHER_REPORT_DATA_INCREMENTS_JSON;
            String jsonData = new ObjectMapper().writeValueAsString(validDataIncrements);
            Files.write(Paths.get(keyName), jsonData.getBytes());

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(Paths.get(keyName)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write data to file", e);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload data to S3 bucket", e);
        }
    }

    public List<WeatherData> validateData(List<WeatherData> dataIncrements) {
        return dataIncrements.stream()
                .filter(this::isValidData)
                .collect(Collectors.toList());
    }

    private boolean isValidData(WeatherData data) {
        return data.getCity() != null && data.getCountry() != null && data.getObservationTime() != null
                && data.getTemperature() != 0 && data.getWeatherCode() != 0 && data.getWeatherDescription() != null
                && data.getWindSpeed() != 0 && data.getWindDegree() != 0 && data.getWindDirection() != null
                && data.getPressure() != 0 && data.getHumidity() != 0 && data.getCloudCover() != 0
                && data.getFeelsLike() != 0 && data.getUvIndex() != 0 && data.getVisibility() != 0
                && data.getLandmarks() != null && data.getMapLink() != null;
    }

    public Long getLastFetchedTimestamp() {
        return lastFetchedTimestamp;
    }
}