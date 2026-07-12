package com.melodybubble.server.profile

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class MediaStorageConfiguration(
    @Value("\${app.media.region:ap-northeast-2}") private val regionName: String,
) {
    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(regionName))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .region(Region.of(regionName))
        .build()
}
