package Email_backend.Email_backend.repository;

import Email_backend.Email_backend.model.OrgEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgEmailConfigRepository extends JpaRepository<OrgEmailConfig, Long> {
}
