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
            //case PROFILES -> props.getStorage().getBuckets().get("profiles");
            //case CERTIFICATES -> props.getStorage().getBuckets().get("certificates");
        };
    }
}