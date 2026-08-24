package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MileageActivityTypeRepository extends JpaRepository<MileageActivityType, Integer> {

    List<MileageActivityType> findAllByActiveTrueOrderByActivityNameAsc();
}
