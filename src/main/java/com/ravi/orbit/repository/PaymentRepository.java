package com.ravi.orbit.repository;

import com.ravi.orbit.entity.Payment;
import com.ravi.orbit.enums.EPaymentMethod;
import com.ravi.orbit.enums.EPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionUuid(String transactionUuid);

    Optional<Payment> findByOrderIdAndPaymentStatus(UUID orderId, EPaymentStatus paymentStatus);

    List<Payment> findAllByPaymentStatusAndPaymentMethod(EPaymentStatus paymentStatus, EPaymentMethod paymentMethod);

    Optional<Payment> findFirstByOrderIdOrderByCreatedDateTimeDesc(UUID orderId);
}
