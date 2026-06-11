package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.UserEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserEmailConfigRepository extends JpaRepository<UserEmailConfig, UUID> {
    Optional<UserEmailConfig> findByEmailAddress(String emailAddress);
}
