import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import 'api_client.dart';
import 'student_models.dart';

class StudentHome extends StatefulWidget {
  const StudentHome({
    required this.apiClient,
    required this.onLogout,
    super.key,
  });

  final StudentApiClient apiClient;
  final VoidCallback onLogout;

  @override
  State<StudentHome> createState() => _StudentHomeState();
}

class _StudentHomeState extends State<StudentHome> {
  static const _titles = [
    'Dashboard',
    'My Fines',
    'Payment History',
    'My Attendance',
    'Profile'
  ];

  int _selectedIndex = 0;
  bool _loading = true;
  String? _error;
  StudentSummary? _summary;
  List<StudentLedgerEntry> _ledger = const [];
  List<StudentAttendanceEntry> _attendance = const [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait<Object>([
        widget.apiClient.loadSummary(),
        widget.apiClient.loadLedger(),
        widget.apiClient.loadAttendance(),
      ]);
      if (!mounted) return;
      setState(() {
        _summary = results[0] as StudentSummary;
        _ledger = results[1] as List<StudentLedgerEntry>;
        _attendance = results[2] as List<StudentAttendanceEntry>;
      });
    } on ApiException catch (failure) {
      if (mounted) setState(() => _error = failure.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _logout() {
    widget.apiClient.logout();
    widget.onLogout();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final summary = _summary;
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_selectedIndex]),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _loading ? null : _loadData,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: _loading && summary == null
          ? const Center(child: CircularProgressIndicator())
          : summary == null
              ? _accountUnavailable(theme)
              : RefreshIndicator(
                  onRefresh: _loadData,
                  child: _pageContent(summary),
                ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) =>
            setState(() => _selectedIndex = index),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.space_dashboard_outlined),
            selectedIcon: Icon(Icons.space_dashboard),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Fines',
          ),
          NavigationDestination(
            icon: Icon(Icons.payments_outlined),
            selectedIcon: Icon(Icons.payments),
            label: 'Payments',
          ),
          NavigationDestination(
            icon: Icon(Icons.event_available_outlined),
            selectedIcon: Icon(Icons.event_available),
            label: 'Attendance',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }

  Widget _accountUnavailable(ThemeData theme) {
    final unlinked = _error?.contains('not linked') ?? false;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  unlinked ? Icons.link_off : Icons.cloud_off_outlined,
                  size: 42,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(height: 16),
                Text(
                  unlinked
                      ? 'Student account not linked'
                      : 'Could not load your account',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  _error ?? 'Try again when the server is available.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 20),
                if (!unlinked)
                  FilledButton.icon(
                    onPressed: _loadData,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Try again'),
                  ),
                TextButton.icon(
                  onPressed: _logout,
                  icon: const Icon(Icons.logout),
                  label: const Text('Log out'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _pageContent(StudentSummary summary) {
    final List<Widget> children = [
      if (_error != null) _errorMessage(_error!),
      switch (_selectedIndex) {
        0 => _dashboard(summary),
        1 => _fines(summary),
        2 => _payments(),
        3 => _attendancePage(),
        _ => _profile(summary),
      },
    ];
    return ListView(
      key: PageStorageKey(_selectedIndex),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
      children: children,
    );
  }

  Widget _dashboard(StudentSummary summary) {
    final attendance = summary.attendance;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Hello, ${summary.student.fullName}',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 6),
        const Text('Here is a summary of your department records.'),
        const SizedBox(height: 18),
        _summaryCard(
          icon: Icons.account_balance_wallet_outlined,
          label: 'Unpaid balance',
          value: _money(summary.unpaidBalance),
          detail: summary.unpaidBalance > 0 ? 'Balance due' : 'No balance due',
        ),
        const SizedBox(height: 12),
        _summaryCard(
          icon: Icons.event_available_outlined,
          label: 'Attendance records',
          value: '${attendance.total}',
          detail: '${attendance.present} present · ${attendance.late} late',
        ),
        const SizedBox(height: 20),
        _sectionTitle('Quick links'),
        const SizedBox(height: 8),
        _quickLink(
          icon: Icons.receipt_long_outlined,
          label: 'View my fines',
          onTap: () => setState(() => _selectedIndex = 1),
        ),
        _quickLink(
          icon: Icons.payments_outlined,
          label: 'View payment history',
          onTap: () => setState(() => _selectedIndex = 2),
        ),
        _quickLink(
          icon: Icons.event_available_outlined,
          label: 'View my attendance',
          onTap: () => setState(() => _selectedIndex = 3),
        ),
      ],
    );
  }

  Widget _fines(StudentSummary summary) {
    final fines = _ledger.where((entry) => entry.kind == 'FINE').toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _summaryCard(
          icon: Icons.account_balance_wallet_outlined,
          label: 'Account payment status',
          value: summary.unpaidBalance > 0 ? 'Balance due' : 'No balance due',
          detail: 'Current unpaid balance: ${_money(summary.unpaidBalance)}',
        ),
        const SizedBox(height: 12),
        _infoNote(
          'Payments reduce your total balance. They are not assigned to individual fines, so a paid status is not available for each fine.',
        ),
        const SizedBox(height: 20),
        _sectionTitle('Fine records'),
        const SizedBox(height: 8),
        if (fines.isEmpty)
          const _EmptyMessage(message: 'No fine records to show.')
        else
          ...fines.map(
            (fine) => _recordCard(
              icon: Icons.receipt_long_outlined,
              title: _entryTitle(fine),
              subtitle: _date(fine.createdAt),
              amount: _money(fine.amount),
              status: fine.correction
                  ? 'Correction'
                  : fine.corrected
                      ? 'Corrected'
                      : 'Recorded',
            ),
          ),
      ],
    );
  }

  Widget _payments() {
    final payments = _ledger.where((entry) => entry.kind == 'PAYMENT').toList();
    if (payments.isEmpty) {
      return const _EmptyMessage(
          message: 'No payments have been recorded yet.');
    }
    return Column(
      children: payments
          .map(
            (payment) => _recordCard(
              icon: Icons.payments_outlined,
              title: _entryTitle(payment),
              subtitle: [
                _date(payment.createdAt),
                if (payment.referenceNumber?.isNotEmpty ?? false)
                  'Reference: ${payment.referenceNumber}',
              ].join('\n'),
              amount: _money(payment.amount),
              status: payment.correction
                  ? 'Correction'
                  : payment.corrected
                      ? 'Corrected'
                      : null,
            ),
          )
          .toList(),
    );
  }

  Widget _attendancePage() {
    if (_attendance.isEmpty) {
      return const _EmptyMessage(
          message: 'No attendance records have been recorded yet.');
    }
    return Column(
      children: _attendance
          .map(
            (record) => _recordCard(
              icon: Icons.event_available_outlined,
              title: record.activityName,
              subtitle: [
                _date(record.activityDate),
                'Checked in ${_time(record.scannedAt)}',
              ].join('\n'),
              amount: '',
              status: _statusLabel(record.status),
            ),
          )
          .toList(),
    );
  }

  Widget _profile(StudentSummary summary) {
    final student = summary.student;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Center(
          child: CircleAvatar(
            radius: 34,
            backgroundColor: Theme.of(context).colorScheme.primary,
            child: Text(
              student.fullName.isEmpty
                  ? '?'
                  : student.fullName[0].toUpperCase(),
              style: const TextStyle(color: Colors.white, fontSize: 24),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Center(
          child: Text(
            student.fullName,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
        ),
        Center(child: Text('@${summary.username}')),
        const SizedBox(height: 20),
        _sectionTitle('Student information'),
        const SizedBox(height: 8),
        _detailCard('Student ID', student.studentNumber),
        if (student.course.isNotEmpty) _detailCard('Course', student.course),
        if (student.yearLevel.isNotEmpty)
          _detailCard('Year level', student.yearLevel),
        if (student.section.isNotEmpty) _detailCard('Section', student.section),
        const SizedBox(height: 12),
        Card(
          child: ListTile(
            leading: const Icon(Icons.qr_code_2),
            title: const Text('My QR code'),
            subtitle:
                const Text('Show your student ID QR code for attendance.'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => StudentQrPage(
                  studentNumber: student.studentNumber,
                  fullName: student.fullName,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: _logout,
            icon: const Icon(Icons.logout),
            label: const Text('Log out'),
          ),
        ),
      ],
    );
  }

  Widget _summaryCard({
    required IconData icon,
    required String label,
    required String value,
    required String detail,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: const Color(0xFFEFF6FF),
              foregroundColor: Theme.of(context).colorScheme.primary,
              child: Icon(icon),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: const TextStyle(color: Color(0xFF475569))),
                  const SizedBox(height: 4),
                  Text(
                    value,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                  const SizedBox(height: 2),
                  Text(detail,
                      style: const TextStyle(color: Color(0xFF64748B))),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _recordCard({
    required IconData icon,
    required String title,
    required String subtitle,
    required String amount,
    required String? status,
  }) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: CircleAvatar(
          backgroundColor: const Color(0xFFEFF6FF),
          foregroundColor: Theme.of(context).colorScheme.primary,
          child: Icon(icon),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 5),
          child: Text(subtitle),
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            if (amount.isNotEmpty)
              Text(amount, style: const TextStyle(fontWeight: FontWeight.w700)),
            if (status != null)
              Text(status,
                  style:
                      const TextStyle(color: Color(0xFF475569), fontSize: 12)),
          ],
        ),
      ),
    );
  }

  Widget _detailCard(String label, String value) {
    return Card(
      child: ListTile(
        title: Text(label, style: const TextStyle(color: Color(0xFF64748B))),
        subtitle: Text(value, style: const TextStyle(color: Color(0xFF0F172A))),
      ),
    );
  }

  Widget _quickLink({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
        title: Text(label),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }

  Widget _sectionTitle(String title) => Text(
        title,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
      );

  Widget _infoNote(String text) {
    return Card(
      color: const Color(0xFFEFF6FF),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.info_outline, color: Color(0xFF1E3A8A)),
            const SizedBox(width: 10),
            Expanded(child: Text(text)),
          ],
        ),
      ),
    );
  }

  Widget _errorMessage(String message) {
    return Card(
      color: const Color(0xFFFFF1F2),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Text(message,
            style: TextStyle(color: Theme.of(context).colorScheme.error)),
      ),
    );
  }

  String _money(double amount) => '₱${amount.toStringAsFixed(2)}';

  String _entryTitle(StudentLedgerEntry entry) =>
      entry.correction ? 'Balance correction' : entry.description;

  String _statusLabel(String status) {
    if (status.isEmpty) return 'Unknown';
    return '${status[0]}${status.substring(1).toLowerCase()}';
  }

  String _date(DateTime? date) {
    if (date == null) return 'Date unavailable';
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    return '${months[date.month - 1]} ${date.day}, ${date.year}';
  }

  String _time(DateTime? dateTime) {
    if (dateTime == null) return 'Time unavailable';
    return TimeOfDay.fromDateTime(dateTime).format(context);
  }
}

class StudentQrPage extends StatelessWidget {
  const StudentQrPage({
    required this.studentNumber,
    required this.fullName,
    super.key,
  });

  final String studentNumber;
  final String fullName;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My QR code')),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      fullName,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 6),
                    Text('Student ID: $studentNumber'),
                    const SizedBox(height: 20),
                    QrImageView(
                      data: studentNumber,
                      version: QrVersions.auto,
                      size: 240,
                      backgroundColor: Colors.white,
                      semanticsLabel: 'Student ID QR code for $studentNumber',
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'This QR code contains your student ID for the department attendance scanner. It does not contain your password or sign-in details.',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _EmptyMessage extends StatelessWidget {
  const _EmptyMessage({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Center(
          child: Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Color(0xFF475569)),
          ),
        ),
      ),
    );
  }
}
