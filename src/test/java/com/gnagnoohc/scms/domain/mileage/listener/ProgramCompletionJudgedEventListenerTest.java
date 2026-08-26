package com.gnagnoohc.scms.domain.mileage.listener;

import com.gnagnoohc.scms.domain.mileage.service.ProgramMileageAccrualService;
import com.gnagnoohc.scms.domain.program.event.ProgramCompletionJudgedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProgramCompletionJudgedEventListenerTest {

    @Mock
    private ProgramMileageAccrualService programMileageAccrualService;

    @InjectMocks
    private ProgramCompletionJudgedEventListener listener;

    @Test
    void completionJudgedEvent_callsMileageAccrualServiceWithApplicationId() {
        listener.handle(new ProgramCompletionJudgedEvent(42));

        verify(programMileageAccrualService).accrueProgramCompletion(42);
    }
}
