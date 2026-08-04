package pe.dcs.app.service.supabase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.util.StorageBucket;

@Service
@RequiredArgsConstructor
public class StorageBucketResolver {

    private final SupabaseProperties props;

    public String resolve(StorageBucket bucket) {
        return switch (bucket) {
            case EVENTS -> props.getStorage().getBuckets().get("events");
            case DOCUMENT_TEMPLATES -> props.getStorage().getBuckets().get("document-templates");
            //case PROFILES -> props.getStorage().getBuckets().get("profiles");
        };
    }
}