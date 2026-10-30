class StudentSummary {
  const StudentSummary({
    required this.username,
    required this.student,
    required this.unpaidBalance,
    required this.attendance,
  });

  final String username;
  final StudentProfile student;
  final double unpaidBalance;
  final AttendanceSummary attendance;

  factory StudentSummary.fromJson(Map<String, dynamic> json) {
    return StudentSummary(
      username: json['username'] as String? ?? '',
      student: StudentProfile.fromJson(
        json['student'] as Map<String, dynamic>? ?? const {},
      ),
      unpaidBalance: _number(json['unpaidBalance']),
      attendance: AttendanceSummary.fromJson(
        json['attendance'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }
}

class StudentProfile {
  const StudentProfile({
    required this.studentNumber,
    required this.fullName,
    required this.course,
    required this.yearLevel,
    required this.section,
  });

  final String studentNumber;
  final String fullName;
  final String course;
  final String yearLevel;
  final String section;

  factory StudentProfile.fromJson(Map<String, dynamic> json) {
    return StudentProfile(
      studentNumber: json['studentNumber'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      course: json['course'] as String? ?? '',
      yearLevel: json['yearLevel'] as String? ?? '',
      section: json['section'] as String? ?? '',
    );
  }
}

class AttendanceSummary {
  const AttendanceSummary({
    required this.total,
    required this.present,
    required this.late,
    required this.absent,
    required this.excused,
  });

  final int total;
  final int present;
  final int late;
  final int absent;
  final int excused;

  factory AttendanceSummary.fromJson(Map<String, dynamic> json) {
    return AttendanceSummary(
      total: _integer(json['total']),
      present: _integer(json['present']),
      late: _integer(json['late']),
      absent: _integer(json['absent']),
      excused: _integer(json['excused']),
    );
  }
}

class StudentLedgerEntry {
  const StudentLedgerEntry({
    required this.kind,
    required this.amount,
    required this.description,
    required this.referenceNumber,
    required this.createdAt,
    required this.correction,
    required this.corrected,
  });

  final String kind;
  final double amount;
  final String description;
  final String? referenceNumber;
  final DateTime? createdAt;
  final bool correction;
  final bool corrected;

  factory StudentLedgerEntry.fromJson(Map<String, dynamic> json) {
    return StudentLedgerEntry(
      kind: json['kind'] as String? ?? '',
      amount: _number(json['amount']),
      description: json['description'] as String? ?? '',
      referenceNumber: json['referenceNumber'] as String?,
      createdAt: DateTime.tryParse(json['createdAt'] as String? ?? ''),
      correction: json['correction'] as bool? ?? false,
      corrected: json['corrected'] as bool? ?? false,
    );
  }
}

class StudentAttendanceEntry {
  const StudentAttendanceEntry({
    required this.activityName,
    required this.activityDate,
    required this.scannedAt,
    required this.status,
  });

  final String activityName;
  final DateTime? activityDate;
  final DateTime? scannedAt;
  final String status;

  factory StudentAttendanceEntry.fromJson(Map<String, dynamic> json) {
    return StudentAttendanceEntry(
      activityName: json['activityName'] as String? ?? '',
      activityDate: DateTime.tryParse(json['activityDate'] as String? ?? ''),
      scannedAt: DateTime.tryParse(json['scannedAt'] as String? ?? ''),
      status: json['status'] as String? ?? '',
    );
  }
}

double _number(Object? value) {
  if (value is num) return value.toDouble();
  return double.tryParse(value?.toString() ?? '') ?? 0;
}

int _integer(Object? value) {
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? 0;
}
