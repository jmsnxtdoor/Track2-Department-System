package edu.department.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class Api {
    private static final String REVERSAL_PREFIX = "REVERSAL:";

    private final StudentRepo students;
    private final LedgerRepo ledger;
    private final FundRepo funds;
    private final EventRepo events;
    private final AttendanceRepo attendance;
    private final AuditService audit;
    private final StudentPortalService studentPortal;

    Api(StudentRepo students, LedgerRepo ledger, FundRepo funds, EventRepo events,
        AttendanceRepo attendance, AuditService audit, StudentPortalService studentPortal) {
        this.students = students;
        this.ledger = ledger;
        this.funds = funds;
        this.events = events;
        this.attendance = attendance;
        this.audit = audit;
        this.studentPortal = studentPortal;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        return Map.of("username", auth.getName(),
                "roles", auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @GetMapping("/students")
    public List<Student> students(@RequestParam(defaultValue = "") String search) {
        return search.isBlank()
                ? students.findAllByOrderByStudentNumberAsc()
                : students.findByStudentNumberContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrderByStudentNumberAsc(
                        search.trim(), search.trim());
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Student createStudent(@Valid @RequestBody Student item, Authentication auth) {
        item.id = null;
        if (students.existsByStudentNumberIgnoreCase(item.studentNumber.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student ID already exists.");
        }
        item.studentNumber = item.studentNumber.trim();
        item.fullName = item.fullName.trim();
        Student saved = students.save(item);
        audit.record(auth.getName(), "STUDENT_CREATED", "Student", saved.id.toString(),
                "studentNumber=" + saved.studentNumber);
        return saved;
    }

    @PutMapping("/students/{id}")
    @Transactional
    public Student editStudent(@PathVariable Long id, @Valid @RequestBody Student input, Authentication auth) {
        Student student = students.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        String studentNumber = input.studentNumber.trim();
        if (students.existsByStudentNumberIgnoreCaseAndIdNot(studentNumber, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student ID already exists.");
        }
        student.studentNumber = studentNumber;
        student.fullName = input.fullName.trim();
        student.course = input.course;
        student.yearLevel = input.yearLevel;
        student.section = input.section;
        Student saved = students.save(student);
        audit.record(auth.getName(), "STUDENT_UPDATED", "Student", saved.id.toString(),
                "studentNumber=" + saved.studentNumber);
        return saved;
    }

    @GetMapping("/students/{id}/ledger")
    public List<LedgerEntry> studentLedger(@PathVariable Long id) {
        return ledger.findByStudentIdOrderByCreatedAtDesc(id);
    }

    @GetMapping("/students/{id}/balance")
    public Map<String, Object> studentBalance(@PathVariable Long id) {
        return Map.of("balance", balance(id));
    }

    private BigDecimal balance(Long studentId) {
        return ledger.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(entry -> entry.kind == LedgerEntry.Kind.FINE ? entry.amount : entry.amount.negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record LedgerInput(
            @NotNull Long studentId,
            @NotNull LedgerEntry.Kind kind,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @Size(max = 255) String referenceNumber,
            @NotBlank @Size(max = 100) String idempotencyKey) {
        LedgerInput(Long studentId, LedgerEntry.Kind kind, BigDecimal amount, String description, String referenceNumber) {
            this(studentId, kind, amount, description, referenceNumber, UUID.randomUUID().toString());
        }
    }

    public record FundInput(
            @NotNull FundEntry.Type type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @NotBlank @Size(max = 100) String idempotencyKey) {
        FundInput(FundEntry.Type type, BigDecimal amount, String description) {
            this(type, amount, description, UUID.randomUUID().toString());
        }
    }

    public record ReasonInput(
            @NotBlank @Size(max = 200) String reason,
            @NotBlank @Size(max = 100) String idempotencyKey) {
        ReasonInput(String reason) {
            this(reason, UUID.randomUUID().toString());
        }
    }

    @GetMapping("/finance/ledger")
    public List<LedgerEntry> allLedger() {
        return ledger.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/finance/ledger")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LedgerEntry addLedger(@Valid @RequestBody LedgerInput input, Authentication auth) {
        return createLedger(input, auth);
    }

    private LedgerEntry createLedger(LedgerInput input, Authentication auth) {
        String requestHash = requestHash("ledger", auth.getName(), input.studentId().toString(), input.kind().name(),
                normalizedAmount(input.amount()), input.description().trim(), normalizedReference(input.referenceNumber()));
        LedgerEntry replay = ledger.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);

        Student student = students.findByIdForUpdate(input.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        replay = ledger.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);
        if (input.kind() == LedgerEntry.Kind.PAYMENT && balance(student.id).compareTo(input.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment exceeds unpaid balance.");
        }
        String reference = input.referenceNumber() == null || input.referenceNumber().isBlank()
                ? "TX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT)
                : input.referenceNumber().trim();
        if (ledger.existsByReferenceNumber(reference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction reference already exists.");
        }

        LedgerEntry entry = new LedgerEntry();
        entry.student = student;
        entry.kind = input.kind();
        entry.amount = input.amount();
        entry.description = input.description().trim();
        entry.referenceNumber = reference;
        entry.idempotencyKey = input.idempotencyKey();
        entry.requestHash = requestHash;
        LedgerEntry saved = ledger.save(entry);
        audit.record(auth.getName(), "LEDGER_ENTRY_CREATED", "LedgerEntry", saved.id.toString(),
                saved.kind + " amount=" + saved.amount + ";reference=" + saved.referenceNumber);
        return saved;
    }

    @PostMapping("/finance/ledger/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LedgerEntry reverseLedger(@PathVariable Long id, @Valid @RequestBody ReasonInput input,
                                     Authentication auth) {
        return createLedgerReversal(id, input, auth);
    }

    private LedgerEntry createLedgerReversal(Long id, ReasonInput input, Authentication auth) {
        String requestHash = requestHash("ledger-reversal", auth.getName(), id.toString(), input.reason().trim());
        LedgerEntry replay = ledger.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);

        LedgerEntry original = ledger.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found."));
        students.findByIdForUpdate(original.student.id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        replay = ledger.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);
        if (original.description.startsWith(REVERSAL_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reversal cannot itself be reversed.");
        }
        String marker = reversalMarker(original.kind.name(), id);
        if (ledger.existsByDescriptionStartingWith(marker)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This transaction has already been reversed.");
        }
        if (original.kind == LedgerEntry.Kind.FINE && balance(original.student.id).compareTo(original.amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The fine cannot be reversed because part of it has already been paid.");
        }

        LedgerEntry reversal = new LedgerEntry();
        reversal.student = original.student;
        reversal.kind = original.kind == LedgerEntry.Kind.FINE
                ? LedgerEntry.Kind.PAYMENT : LedgerEntry.Kind.FINE;
        reversal.amount = original.amount;
        reversal.description = marker + input.reason().trim();
        reversal.referenceNumber = "REV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        reversal.idempotencyKey = input.idempotencyKey();
        reversal.requestHash = requestHash;
        LedgerEntry saved = ledger.save(reversal);
        audit.record(auth.getName(), "LEDGER_ENTRY_REVERSED", "LedgerEntry", id.toString(),
                "reversalId=" + saved.id + ";amount=" + saved.amount + ";reason=" + input.reason().trim());
        return saved;
    }

    @GetMapping("/finance/funds")
    public List<FundEntry> allFunds() {
        return funds.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/finance/funds")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public FundEntry addFund(@Valid @RequestBody FundInput input, Authentication auth) {
        return createFund(input, auth);
    }

    private FundEntry createFund(FundInput input, Authentication auth) {
        validateFund(input.type(), input.amount(), input.description());
        funds.acquireFundLedgerLock();
        funds.findAllForUpdate();
        String requestHash = requestHash("fund", auth.getName(), input.type().name(), normalizedAmount(input.amount()),
                input.description().trim());
        FundEntry replay = funds.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);
        if (input.type() == FundEntry.Type.DISBURSEMENT
                && fundBalance().compareTo(input.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient fund balance.");
        }
        FundEntry entry = new FundEntry();
        entry.type = input.type();
        entry.amount = input.amount();
        entry.description = input.description().trim();
        entry.createdAt = java.time.LocalDateTime.now();
        entry.idempotencyKey = input.idempotencyKey();
        entry.requestHash = requestHash;
        FundEntry saved = funds.save(entry);
        audit.record(auth.getName(), "FUND_ENTRY_CREATED", "FundEntry", saved.id.toString(),
                saved.type + " amount=" + saved.amount);
        return saved;
    }

    @PostMapping("/finance/funds/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public FundEntry reverseFund(@PathVariable Long id, @Valid @RequestBody ReasonInput input,
                                 Authentication auth) {
        return createFundReversal(id, input, auth);
    }

    private FundEntry createFundReversal(Long id, ReasonInput input, Authentication auth) {
        funds.acquireFundLedgerLock();
        funds.findAllForUpdate();
        String requestHash = requestHash("fund-reversal", auth.getName(), id.toString(), input.reason().trim());
        FundEntry replay = funds.findByIdempotencyKey(input.idempotencyKey()).orElse(null);
        if (replay != null) return replayIfSame(replay, requestHash);
        FundEntry original = funds.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund entry not found."));
        if (original.description.startsWith(REVERSAL_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reversal cannot itself be reversed.");
        }
        String marker = reversalMarker(original.type.name(), id);
        if (funds.existsByDescriptionStartingWith(marker)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This fund entry has already been reversed.");
        }
        if (original.type != FundEntry.Type.DISBURSEMENT
                && fundBalance().compareTo(original.amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The fund entry cannot be reversed because those funds have already been spent.");
        }

        FundEntry reversal = new FundEntry();
        reversal.type = original.type == FundEntry.Type.DISBURSEMENT
                ? FundEntry.Type.ADDITION : FundEntry.Type.DISBURSEMENT;
        reversal.amount = original.amount;
        reversal.description = marker + input.reason().trim();
        reversal.idempotencyKey = input.idempotencyKey();
        reversal.requestHash = requestHash;
        FundEntry saved = funds.save(reversal);
        audit.record(auth.getName(), "FUND_ENTRY_REVERSED", "FundEntry", id.toString(),
                "reversalId=" + saved.id + ";amount=" + saved.amount + ";reason=" + input.reason().trim());
        return saved;
    }

    private static String normalizedAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String normalizedReference(String referenceNumber) {
        return referenceNumber == null || referenceNumber.isBlank() ? "" : referenceNumber.trim();
    }

    private static String requestHash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available.", impossible);
        }
    }

    private static LedgerEntry replayIfSame(LedgerEntry entry, String requestHash) {
        if (!requestHash.equals(entry.requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key was already used with different transaction details.");
        }
        return entry;
    }

    private static FundEntry replayIfSame(FundEntry entry, String requestHash) {
        if (!requestHash.equals(entry.requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key was already used with different transaction details.");
        }
        return entry;
    }

    private void validateFund(FundEntry.Type type, BigDecimal amount, String description) {
        if (type == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > 2 || amount.precision() > 14
                || description == null || description.isBlank() || description.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fund entry.");
        }
    }

    private BigDecimal fundBalance() {
        return fundBalance(funds.findAll());
    }

    private BigDecimal fundBalance(List<FundEntry> entries) {
        return entries.stream()
                .map(entry -> entry.type == FundEntry.Type.DISBURSEMENT
                        ? entry.amount.negate() : entry.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        Set<String> roles = auth.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet());
        boolean financialAccess = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_TREASURER");
        boolean attendanceAccess = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_OFFICER");
        return dashboard(financialAccess, attendanceAccess);
    }

    Map<String, Object> dashboard() {
        return dashboard(true, true);
    }

    private Map<String, Object> dashboard(boolean financialAccess, boolean attendanceAccess) {
        Map<String, Object> result = new LinkedHashMap<>();
        long studentCount = students.count();
        result.put("students", studentCount);
        if (financialAccess) {
            List<LedgerEntry> entries = ledger.findAllByOrderByCreatedAtDesc();
            BigDecimal unpaid = students.findAll().stream()
                    .map(student -> balance(student.id).max(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put("collections", netCollections(entries));
            result.put("unpaid", unpaid);
            result.put("fundBalance", fundBalance());
            result.put("collectionChart", collectionChart(entries));
            result.put("recent", entries.stream().limit(5).toList());
        }
        if (attendanceAccess) {
            List<DepartmentEvent> activeEvents = events.findByActiveTrue();
            long activeEventCount = activeEvents.size();
            Set<Long> activeEventIds = activeEvents.stream().map(event -> event.id).collect(Collectors.toSet());
            long eligibleAttendance = attendance.count();
            long presentOrLate = attendance.findAll().stream()
                    .filter(item -> activeEventIds.contains(item.event.id))
                    .filter(item -> item.status == Attendance.Status.PRESENT || item.status == Attendance.Status.LATE)
                    .count();
            double attendanceRate = activeEventCount == 0 || studentCount == 0 ? 0
                    : (double) presentOrLate / ((double) activeEventCount * studentCount) * 100;
            result.put("attendanceScans", eligibleAttendance);
            result.put("attendanceRate", Math.round(attendanceRate * 10.0) / 10.0);
        }
        return result;
    }

    private BigDecimal netCollections(List<LedgerEntry> entries) {
        BigDecimal payments = entries.stream()
                .filter(entry -> entry.kind == LedgerEntry.Kind.PAYMENT
                        && !entry.description.startsWith(REVERSAL_PREFIX))
                .map(entry -> entry.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentReversals = entries.stream()
                .filter(entry -> entry.kind == LedgerEntry.Kind.FINE
                        && entry.description.startsWith(REVERSAL_PREFIX + LedgerEntry.Kind.PAYMENT.name() + ":"))
                .map(entry -> entry.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return payments.subtract(paymentReversals);
    }

    private List<Map<String, Object>> collectionChart(List<LedgerEntry> entries) {
        YearMonth current = YearMonth.now();
        Map<YearMonth, BigDecimal> totals = new TreeMap<>();
        for (int i = 5; i >= 0; i--) {
            totals.put(current.minusMonths(i), BigDecimal.ZERO);
        }
        for (LedgerEntry entry : entries) {
            YearMonth month = YearMonth.from(entry.createdAt);
            if (!totals.containsKey(month)) {
                continue;
            }
            if (entry.kind == LedgerEntry.Kind.PAYMENT && !entry.description.startsWith(REVERSAL_PREFIX)) {
                totals.merge(month, entry.amount, BigDecimal::add);
            } else if (entry.kind == LedgerEntry.Kind.FINE
                    && entry.description.startsWith(REVERSAL_PREFIX + LedgerEntry.Kind.PAYMENT.name() + ":")) {
                totals.merge(month, entry.amount.negate(), BigDecimal::add);
            }
        }
        return totals.entrySet().stream()
                .map(entry -> Map.<String, Object>of("month", entry.getKey().toString(), "amount", entry.getValue()))
                .toList();
    }

    @GetMapping("/attendance/events")
    public List<DepartmentEvent> events() {
        return events.findAllByOrderByEventDateDesc();
    }

    @PostMapping("/attendance/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DepartmentEvent newEvent(@Valid @RequestBody DepartmentEvent event, Authentication auth) {
        event.id = null;
        DepartmentEvent saved = events.save(event);
        audit.record(auth.getName(), "ATTENDANCE_EVENT_CREATED", "DepartmentEvent", saved.id.toString(),
                "title=" + saved.title);
        return saved;
    }

    public record ScanInput(
            @NotNull Long eventId,
            @NotBlank @Size(max = 255) String studentNumber,
            Attendance.Status status) {}

    @PostMapping("/attendance/scan")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Attendance scan(@Valid @RequestBody ScanInput input, Authentication auth) {
        return recordAttendance(input, auth);
    }

    @PostMapping("/attendance/records")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Attendance manualAttendance(@Valid @RequestBody ScanInput input, Authentication auth) {
        return recordAttendance(input, auth);
    }

    private Attendance recordAttendance(ScanInput input, Authentication auth) {
        DepartmentEvent event = events.findById(input.eventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
        if (!event.active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event inactive.");
        }
        Student student = students.findByStudentNumber(input.studentNumber().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        if (attendance.existsByStudentIdAndEventId(student.id, event.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already checked in.");
        }
        Attendance record = new Attendance();
        record.event = event;
        record.student = student;
        record.status = input.status() == null ? Attendance.Status.PRESENT : input.status();
        Attendance saved;
        try {
            saved = attendance.saveAndFlush(record);
        } catch (DataIntegrityViolationException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already checked in.", failure);
        }
        audit.record(auth.getName(), "ATTENDANCE_RECORDED", "Attendance", saved.id.toString(),
                "eventId=" + event.id + ";studentId=" + student.id + ";status=" + saved.status);
        return saved;
    }

    public record AttendanceStatusInput(@NotNull Attendance.Status status) {}

    @PutMapping("/attendance/records/{id}")
    @Transactional
    public Attendance updateAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceStatusInput input,
                                        Authentication auth) {
        Attendance record = attendance.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found."));
        record.status = input.status();
        Attendance saved = attendance.save(record);
        audit.record(auth.getName(), "ATTENDANCE_STATUS_UPDATED", "Attendance", saved.id.toString(),
                "status=" + saved.status);
        return saved;
    }

    @GetMapping("/attendance/records")
    public List<Attendance> records() {
        return attendance.findAll();
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> report(Authentication auth) {
        return dashboard(auth);
    }

    @GetMapping("/reports/students")
    public List<Student> studentReport() {
        return students.findAllByOrderByStudentNumberAsc();
    }

    @GetMapping("/reports/finance")
    public Map<String, Object> financeReport() {
        List<LedgerEntry> entries = ledger.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> balances = students.findAllByOrderByStudentNumberAsc().stream()
                .map(student -> Map.<String, Object>of(
                        "studentNumber", student.studentNumber,
                        "fullName", student.fullName,
                        "unpaidBalance", balance(student.id).max(BigDecimal.ZERO)))
                .toList();
        return Map.of("transactions", entries, "balances", balances, "totalCollections", netCollections(entries));
    }

    @GetMapping("/reports/funds")
    public List<FundEntry> fundsReport() {
        return funds.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/reports/attendance")
    public List<Map<String, Object>> attendanceReport() {
        return events.findAllByOrderByEventDateDesc().stream().map(event -> {
            List<Attendance> records = attendance.findByEventId(event.id);
            Map<Attendance.Status, Long> byStatus = records.stream()
                    .collect(Collectors.groupingBy(record -> record.status, () -> new EnumMap<>(Attendance.Status.class),
                            Collectors.counting()));
            return Map.<String, Object>of(
                    "eventId", event.id,
                    "title", event.title,
                    "eventDate", event.eventDate,
                    "active", event.active,
                    "total", records.size(),
                    "present", byStatus.getOrDefault(Attendance.Status.PRESENT, 0L),
                    "late", byStatus.getOrDefault(Attendance.Status.LATE, 0L),
                    "absent", byStatus.getOrDefault(Attendance.Status.ABSENT, 0L),
                    "excused", byStatus.getOrDefault(Attendance.Status.EXCUSED, 0L));
        }).toList();
    }

    private String reversalMarker(String type, Long id) {
        return REVERSAL_PREFIX + type + ":" + id + ":";
    }

    @GetMapping("/students/me/summary")
    public StudentPortalService.StudentSummary studentSummary(Authentication auth) {
        return studentPortal.summary(auth.getName());
    }

    @GetMapping("/students/me/ledger")
    public List<StudentPortalService.StudentLedgerEntry> studentLedger(Authentication auth) {
        return studentPortal.ledger(auth.getName());
    }

    // Kept for compatibility with the earlier student portal placeholder.
    @GetMapping("/students/me/balance")
    public Object myBalance() {
        return Map.of("message", "View your current balance in the student summary.");
    }

    @GetMapping("/students/me/attendance")
    public List<StudentPortalService.StudentAttendance> myAttendance(Authentication auth) {
        return studentPortal.attendance(auth.getName());
    }
}
