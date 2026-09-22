INSERT INTO job_posting (
    company_account_id,
    ncs_code_id,
    region_code_id,
    reviewed_by,
    posting_title,
    job_description,
    recruitment_count,
    employment_type,
    salary_text,
    qualification_data,
    application_starts_at,
    application_ends_at,
    posting_type,
    benefit_type,
    review_status,
    reviewed_at,
    rejection_reason,
    posting_status,
    submitted_at,
    published_at,
    created_at,
    updated_at,
    file_group_id
)
WITH ncs_meta (code_id, role_titles, main_tasks, cert_list) AS (
    VALUES
        (447, ARRAY['B2B 사업개발 매니저', '공공 제안서 기획자', 'WBS 프로젝트 매니저(PM)', '사업 리스크 관리 담당'],
         ARRAY['공공/민간 프로젝트 제안서 작성 및 기술 PT', '사업비 집행 공수 산정 및 일정 조율', 'WBS 기반 산출물 검수 및 협력사 관리'],
         ARRAY['PMP', '컴퓨터활용능력 1급', '정보처리기사']),
        (448, ARRAY['재무회계 결산 담당자', '인사총무/노무 관리자', '경영전략 기획 담당', '자금 운용 실무자'],
         ARRAY['월/분기 재무제표 작성 및 부가세 신고', '근태 관리, 4대보험 및 급여 정산', '손익 분석 리포트 작성 및 비용 관리'],
         ARRAY['전산세무 1급', '재경관리사', '전산회계 1급']),
        (449, ARRAY['기업 여신심사역', '자산운용 리스크 관리원', '금융상품 상담 전문가', '투자 심사 보조'],
         ARRAY['대출 심사 및 신용평가 모형 분석', '금융 포트폴리오 리스크 모니터링', '자산 유동화 및 금리 변동 분석'],
         ARRAY['AFPK', 'CFP', '투자자산운용사']),
        (450, ARRAY['대학 학사운영 행정원', '온라인 교육 콘텐츠 기획자', '사회통계 리서치 연구원', '학습 멘토링 코디네이터'],
         ARRAY['교안 개발 및 학사 관리 시스템 운영', '설문조사 데이터 통계 분석(SPSS/R)', '교육 만족도 평가 및 피드백 환류'],
         ARRAY['사회조사분석사 2급', '평생교육사', '직업상담사 2급']),
        (451, ARRAY['기업 법무 지원 담당', '소방시설 점검 엔지니어', '계약 검토 실무자', '방재 안전 관리원'],
         ARRAY['상업 계약서 법률 리스크 검토', '소방시설 종합정밀점검 및 작동점검', '소송 서류 취합 및 법무 행정 지원'],
         ARRAY['소방설비기사', '산업안전기사', '행정사']),
        (452, ARRAY['병원 원무 행정 담당', '의무기록/보험심사 청구원', '임상 데이터 관리자(CDM)', '건강검진센터 코디네이터'],
         ARRAY['진료비 수납 및 심평원 급여 청구', '전자의무기록(EMR) 데이터 정제 및 보관', '검진 예약 관리 및 고객 케어'],
         ARRAY['병원행정사', '보험의료심사평가사', '의무기록사(보건의료정보관리사)']),
        (453, ARRAY['종합사회복지관 사례관리사', '청소년 복지 프로그램 기획자', '노인장기요양 코디네이터', '지역사회 복지 실무자'],
         ARRAY['취약계층 상담 및 맞춤형 사례 관리', '복지 공모사업 제안서 작성 및 집행', '자원봉사자 연계 및 후원처 관리'],
         ARRAY['사회복지사 1급', '사회복지사 2급', '직업상담사 2급']),
        (454, ARRAY['UI/UX 서비스 디자이너', '모션그래픽/영상 편집자', '브랜드 비주얼 기획자', '캐릭터/원화 그래픽 아티스트'],
         ARRAY['Figma 기반 와이어프레임 및 디자인 시스템 구축', '프리미어/애프터이펙트 영상 콘텐츠 컷편집', '브랜드 굿즈 디자인 및 인쇄 감리'],
         ARRAY['GTQ 1급', '시각디자인기사', '컬러리스트기사']),
        (455, ARRAY['물류센터 배차 관리자', '글로벌 포워딩 오퍼레이터', '화물 수송 운영원', 'SCM 공급망 관리자'],
         ARRAY['일일 운송 차량 배차 및 이동 동선 최적화', '수출입 통관 서류 검토 및 B/L 발행', '물류센터 입출고 재고 회전율 점검'],
         ARRAY['물류관리사', '유통관리사 2급', '보세사']),
        (456, ARRAY['B2B 솔루션 기술영업 담당', '대형 유통망 채널 MD', '해외 영업 바이어 상담원', '기업 영업 지원 매니저'],
         ARRAY['고객사 미팅 및 솔루션 도입 견적 제안', '온라인 입점 채널 프로모션 기획 및 매출 마감', '해외 바이어 오더 수주 및 무역 서신 대응'],
         ARRAY['유통관리사 2급', '무역영어 1급', '국제무역사']),
        (457, ARRAY['첨단 빌딩 시설보안 관리자', '산업단지 경비 지도원', '특수 시설 방재요원', '보안시스템 관제 운영원'],
         ARRAY['출입 인원 및 차량 검문 보안 통제', 'CCTV 중앙 관제 및 비상 상황 전파', '빌딩 종합 방범 설비 정기 순찰'],
         ARRAY['일반경비지도사', '신변보호사', '소방안전관리자 2급']),
        (458, ARRAY['특급호텔 프론트 오피스', '여행 패키지 상품 기획자', '리조트 레저 시설 매니저', '피트니스 트레이너/운영관리'],
         ARRAY['외국인 투숙객 체크인/체크아웃 및 컨시어지', '국내외 여행 코스 개발 및 인솔 예약 조율', '레저 시설 안전 점검 및 회원권 관리'],
         ARRAY['호텔서비스사', '관광통역안내사', '생활스포츠지도사 2급']),
        (459, ARRAY['F&B 프랜차이즈 수퍼바이저', '외식 신메뉴 개발 R&D', '양식/한식 메인 셰프', '레스토랑 총괄 캡틴'],
         ARRAY['식자재 원가율 분석 및 발주 시스템 관리', '신메뉴 레시피 표준화 및 조리 매뉴얼화', '주방 위생(HACCP) 관리 및 홀 서비스 총괄'],
         ARRAY['한식조리기능사', '양식조리기능사', '위생사']),
        (460, ARRAY['건축 현장 공정관리 기사', '토목 CAD 도면 검토원', '건설 안전 감리 실무자', '플랜트 배관 시공 담당'],
         ARRAY['시공 도면 검토 및 공정률 스케줄 관리', '현장 근로자 안전 수칙 준수 점검 및 TBM 주관', '자재 입고 검수 및 시공 하자 내역 보수 지시'],
         ARRAY['건축기사', '토목기사', '건설안전기사']),
        (461, ARRAY['3D CAD 기계 설계 엔지니어', '자동화 생산라인 설비보전원', '공조냉동 공조설비 관리자', '정밀 가공 CAM 프로그래머'],
         ARRAY['SolidWorks/CATIA 기반 기계 부품 모델링', 'PLC 기반 스마트팩토리 모터 및 센서 유지보수', 'CNC 머시닝센터 가공 지코드(G-code) 생성'],
         ARRAY['일반기계기사', '공조냉동기계기사', '기계설계산업기사']),
        (462, ARRAY['금속 열처리 공정 기술자', '신소재 물성 분석 연구원', '비파괴 검사(NDT) 오퍼레이터', '표면처리/도금 관리원'],
         ARRAY['원소재 인장강도 및 경도 테스트 분석', 'X-ray/초음파 비파괴 용접 결함 검출', '열처리로 온도 프로파일 모니터링'],
         ARRAY['금속재료기사', '비파괴검사기사', '열처리기능사']),
        (463, ARRAY['바이오 의약품 배양 연구원', '화학제품 품질관리(QC/QA)', '위험물 안전 관리 책임자', '고분자 합성 연구원'],
         ARRAY['HPLC/GC 기기 활용 성분 정량 분석', 'GMP 가이드라인 기반 시험성적서 작성', '화학물질 취급 기준 점검 및 MSDS 관리'],
         ARRAY['화학분석기사', '위험물산업기사', '바이오화학제품제조기사']),
        (464, ARRAY['패션 브랜드 테크니컬 디자이너', '원단 소싱 및 원사 분석원', '의류 패턴 제작 모델리스트', '봉제 생산 공정 관리자'],
         ARRAY['의류 작업지시서(Tech Pack) 작성 및 스펙 측정', '원단 세탁 견뢰도 및 혼용률 테스트', '샘플 가봉 피팅 및 패턴 수정 전개'],
         ARRAY['패션디자인산업기사', '섬유공학기사', '양복산업기사']),
        (465, ARRAY['임베디드 펌웨어 개발자', 'PCB 하드웨어 회로 설계원', '공장 전력 수배전반 점검원', '반도체 공정 계측 엔지니어'],
         ARRAY['C/C++ 기반 MCU 펌웨어 코딩 및 통신 테스트', 'OrCAD 활용 회로도 작성 및 아트웍(Artwork) 검토', '특고압 수전 설비 안전점검 및 역률 관리'],
         ARRAY['전기기사', '전자기사', '전기공사기사']),
        (466, ARRAY['Spring Boot 백엔드 엔지니어', 'React 프론트엔드 개발자', 'AWS 클라우드 인프라 아키텍트', 'AI 데이터 엔지니어'],
         ARRAY['REST API 설계, 분산 락 및 Redis 캐싱 구축', 'SPA 컴포넌트 상태 관리 및 비동기 API 연동', 'Docker/Kubernetes 기반 CI/CD 자동화 파이프라인 구축'],
         ARRAY['정보처리기사', 'SQLD', 'AWS Certified Solutions Architect']),
        (467, ARRAY['식품 HACCP 인증 품질 관리원', '식음료 신제품 레시피 개발원', '발효 가공 생산 공정 관리자', '식품 영양성분 분석 연구원'],
         ARRAY['HACCP 중요관리점(CCP) 모니터링 및 일지 작성', '시제품 관능 평가 및 유통기한 설정 실험', '원료 배합비 산출 및 생산 수율 개선'],
         ARRAY['식품기사', '식품산업기사', '위생사']),
        (468, ARRAY['시스템 가구 3D 렌더링 디자이너', '인쇄 패키지 컬러 감리원', '목재 CNC 가공 오퍼레이터', '공예 상품 프로덕트 디자이너'],
         ARRAY['가구 모듈러 규격 도면화 및 조립 설명서 제작', '오프셋 인쇄 CTP 출력 및 색상 감리 매칭', '목재 재단 및 엣지 밴딩 가공 품질 관리'],
         ARRAY['시각디자인기사', '가구설계제도사', '컬러리스트산업기사']),
        (469, ARRAY['사업장 산업안전 관리자', '대기/수질 환경오염 방지 기술원', 'ESG 탄소배출량 산정원', '신재생에너지 발전설비 운영원'],
         ARRAY['중대재해처벌법 대비 위험성평가 실시 및 교육', '폐수/대기 배출시설 자가측정 및 약품 투입 관리', '태양광/풍력 인버터 효율 점검 및 안전진단'],
         ARRAY['산업안전기사', '대기환경기사', '수질환경기사']),
        (470, ARRAY['스마트팜 ICT 환경제어 관리자', '시설원예 재배 기술 지도원', '임산물 유통 가공 매니저', '수산 양식 생육 모니터링원'],
         ARRAY['복합환경제어기 양액기/온습도 센서 모니터링', '작물 병해충 예찰 및 친환경 방제 처방', '스마트 온실 생육 데이터 수집 및 출하 일정 조율'],
         ARRAY['유기농업기사', '종자기사', '농작업안전보건기사'])
),
     region_indexed AS (
         -- 17개 지역 코드에 0부터 16까지 순번 사전 부여
         SELECT code_id, code_name,
                (ROW_NUMBER() OVER (ORDER BY code_id) - 1)::int AS r_idx
         FROM common_code
         WHERE code_group = 'REGION_CODE'
     ),
     ncs_indexed AS (
         -- 24개 NCS 코드에 0부터 23까지 순번 사전 부여
         SELECT
             m.code_id,
             cc.code_name,
             (ROW_NUMBER() OVER (ORDER BY m.code_id) - 1)::int AS n_idx,
             m.role_titles,
             m.main_tasks,
             m.cert_list
         FROM ncs_meta m
                  JOIN common_code cc ON cc.code_id = m.code_id
     ),
     grid AS (
         -- 각 NCS마다 30개씩 생성하고, r_idx를 수학적으로 계산 (WHERE 절 없이 조인 준비)
         SELECT
             n.code_id AS ncs_code_id,
             n.code_name AS ncs_name,
             n.role_titles,
             n.main_tasks,
             n.cert_list,
             s.seq,
             ((n.n_idx * 30 + s.seq) % 17)::int AS target_r_idx
         FROM ncs_indexed n
                  CROSS JOIN generate_series(1, 30) s(seq)
     ),
     final_rows AS (
         -- 계산된 target_r_idx로 17개 지역과 직접 JOIN
         SELECT
             g.*,
             r.code_id AS region_code_id,
             r.code_name AS region_name
         FROM grid g
                  JOIN region_indexed r ON r.r_idx = g.target_r_idx
     )
SELECT
    COALESCE((SELECT company_account_id FROM company_account LIMIT 1), 2),
    f.ncs_code_id,
    f.region_code_id,
    COALESCE((SELECT user_id FROM app_user WHERE user_type IN ('STAFF', 'ADMIN') LIMIT 1), 11),
    '[' || f.region_name || '] ' || f.role_titles[1 + (f.seq % cardinality(f.role_titles))] || ' 채용 (No.' || (3000 + (f.ncs_code_id * 10) + f.seq) || ')',
    '【직무 개요】' || chr(10) ||
    '- ' || f.ncs_name || ' 전문 역량을 바탕으로 ' || f.role_titles[1 + (f.seq % cardinality(f.role_titles))] || ' 포지션의 핵심 과업을 수행합니다.' || chr(10) ||
    '- 표준 가이드 준수 및 체계적인 프로세스 관리로 업무 성과를 창출합니다.' || chr(10) || chr(10) ||
    '【주요 업무】' || chr(10) ||
    '1. ' || f.main_tasks[1] || chr(10) ||
    '2. ' || f.main_tasks[2] || chr(10) ||
    '3. ' || f.main_tasks[3] || chr(10) || chr(10) ||
    '【우대 사항】' || chr(10) ||
    '- ' || array_to_string(f.cert_list, ', ') || ' 자격증 소지자 우대' || chr(10) ||
    '- 관련 학과 전공자 또는 ' || f.ncs_name || ' 유관 프로젝트/실무 유경험자' || chr(10) ||
    '- 적극적인 커뮤니케이션 역량 및 데이터 기반 문제 해결 능력 보유자',
    1 + (f.seq % 4),
    (ARRAY['정규직', '정규직', '계약직', '인턴'])[1 + (f.seq % 4)],
    CASE (f.seq % 4)
        WHEN 0 THEN '3,400만원 ~ 4,000만원'
        WHEN 1 THEN '4,200만원 ~ 5,000만원'
        WHEN 2 THEN '회사 내규에 따름 (면접 후 협의)'
        ELSE NULL
        END,
    jsonb_build_object(
            'requiredEducation', (ARRAY['학력 무관', '학사 이상', '전문학사 이상'])[1 + (f.seq % 3)],
            'requiredExperience', (ARRAY['신입', '경력 1~3년', '경력 무관'])[1 + (f.seq % 3)],
            'certifications', to_jsonb(f.cert_list)
    ),
    CURRENT_TIMESTAMP - (INTERVAL '1 day' * (f.seq % 7)),
    CURRENT_TIMESTAMP + (INTERVAL '1 day' * (30 + (f.seq % 60))),
    (ARRAY['GENERAL', 'RECOMMENDED'])[1 + (f.seq % 2)],
    (ARRAY[NULL, 'SCHOLARSHIP', 'HOUSING_SUPPORT'])[1 + (f.seq % 3)],
    'APPROVED',
    CURRENT_TIMESTAMP - (INTERVAL '1 day' * (f.seq % 5)),
    NULL,
    'PUBLISHED',
    CURRENT_TIMESTAMP - (INTERVAL '1 day' * (f.seq % 5)),
    CURRENT_TIMESTAMP - (INTERVAL '1 day' * (f.seq % 5)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM final_rows f;

COMMIT;