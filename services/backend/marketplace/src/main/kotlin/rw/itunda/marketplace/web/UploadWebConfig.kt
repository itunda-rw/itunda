package rw.itunda.marketplace.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Serves what [UploadController] saves -- see that file's own doc comment. */
@Configuration
class UploadWebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadDir = System.getenv("UPLOAD_DIR") ?: "/uploads"
        registry.addResourceHandler("/api/v1/uploads/**")
            .addResourceLocations("file:$uploadDir/")
    }
}
