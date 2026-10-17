package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.OtpChannel;
import com.flowbinary.platformservice.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findByVerificationTokenAndVerifiedAtIsNull(String verificationToken);

    @Query("""
            SELECT COUNT(o) FROM OtpVerification o
            WHERE o.target = :target AND o.channel = :channel AND o.createdAt >= :since
            """)
    long countRecentRequests(@Param("target") String target,
                              @Param("channel") OtpChannel channel,
                              @Param("since") Instant since);

    Optional<OtpVerification> findFirstByTargetAndChannelOrderByCreatedAtDesc(String target, OtpChannel channel);

    Optional<OtpVerification> findFirstByTargetAndChannelAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            String target, OtpChannel channel, String purpose);

    /**
     * Supersedes any outstanding code for the target. Rows are expired rather than
     * deleted so they still count towards the send rate limit and cooldown.
     */
    @Modifying
    @Query("""
            UPDATE OtpVerification o SET o.expiresAt = :now
            WHERE o.target = :target AND o.channel = :channel AND o.verifiedAt IS NULL AND o.expiresAt > :now
            """)
    void expireUnverifiedByTargetAndChannel(@Param("target") String target,
                                            @Param("channel") OtpChannel channel,
                                            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
