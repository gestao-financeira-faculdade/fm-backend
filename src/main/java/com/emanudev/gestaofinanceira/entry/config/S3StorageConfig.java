package com.emanudev.gestaofinanceira.entry.config;

import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(ApplicationProperties applicationProperties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(applicationProperties.getS3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                applicationProperties.getS3AccessKey(),
                                applicationProperties.getS3SecretKey()
                        )
                ))
                .forcePathStyle(applicationProperties.isS3PathStyleAccessEnabled());

        String endpoint = applicationProperties.getS3Endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
