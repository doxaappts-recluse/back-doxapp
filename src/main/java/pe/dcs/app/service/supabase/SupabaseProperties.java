package pe.dcs.app.service.supabase;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "supabase")
@Getter
@Setter
public class SupabaseProperties {

    private String url;
    private String serviceKey;
    private Storage storage;

    @Getter
    @Setter
    public static class Storage {
        private Map<String, String> buckets;
    }
}