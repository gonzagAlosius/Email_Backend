package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.UserEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserEmailConfigRepository extends JpaRepository<UserEmailConfig, UUID> {
    Optional<UserEmailConfig> findByEmailAddress(String emailAddress);

    /**
     * Finds all active users who have registered a OneSignal subscription ID.
     * Used by InboxPollingService to know which users need new-email checks.
     */
    List<UserEmailConfig> findByIsActiveTrueAndOneSignalSubscriptionIdIsNotNull();
    
    /**
     * Finds all active users, regardless of push notification status.
     */
    List<UserEmailConfig> findByIsActiveTrue();
}
