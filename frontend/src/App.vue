<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import axios from 'axios';
import { useDisplay } from 'vuetify';
import { setRequestFailure } from './requestState.js';
import { saveUserAccount } from './userAccounts.js';
import { buildExternalApiRequest } from './externalApi.js';
import { clearIdempotencyKey, getIdempotencyKey } from './idempotency.js';

const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });
const { mdAndUp } = useDisplay();
const username = ref('');
const password = ref('');
const auth = ref('');
const role = ref('');
let activeRequests = 0;
const page = ref('Dashboard');
const drawerOpen = ref(false);
const loading = ref(false);
const ledgerSubmitting = ref(false);
const fundSubmitting = ref(false);
const error = ref('');
const notice = ref('');
const students = ref([]);
const ledger = ref([]);
const funds = ref([]);
const events = ref([]);
const records = ref([]);
const stats = ref({});
const studentPortal = ref({ summary: null, ledger: [], attendance: [] });
const users = ref([]);
const auditLogs = ref([]);
const reportData = ref({ students: [], finance: null, funds: [], attendance: [] });
const reportKind = ref('Students');
const studentSearch = ref('');
const studentDialog = ref(false);
const editingStudent = ref(null);
const studentForm = ref({ studentNumber: '', fullName: '', course: '', yearLevel: '', section: '' });
const entry = ref({ studentId: null, amount: null, description: '', referenceNumber: '' });
const fund = ref({ type: 'ADDITION', amount: null, description: '' });
const eventForm = ref({ title: '', eventDate: new Date().toISOString().slice(0, 10), active: true });
const scan = ref({ eventId: null, studentNumber: '', status: 'PRESENT' });
const scannerElement = ref(false);
const qrScanner = ref(null);
const userDialog = ref(false);
const editingUser = ref(null);
const userForm = ref({ username: '', role: 'STUDENT', password: '', newPassword: '', enabled: true, studentId: null });
const externalApiUrl = ref('');
const externalApiAuthenticationType = ref('NONE');
const externalApiCredential = ref('');
const externalApiResult = ref(null);
const externalApiStatus = ref(null);
const externalApiError = ref('');
const externalApiLoading = ref(false);
const reverseDialog = ref(false);
const reverseTarget = ref(null);
const reverseType = ref('');
const reverseReason = ref('');

const pageLabels = {
  Fines: 'Student Fines',
  Collections: 'Payments',
  Collectibles: 'Unpaid Balances',
  Funds: 'Department Funds',
  Disbursements: 'Expenses',
  'Users & Roles': 'User Accounts',
};
const roleLabels = {
  ADMIN: 'Administrator',
  TREASURER: 'Treasurer',
  OFFICER: 'Officer',
  STUDENT: 'Student',
};
const navigationIcons = {
  Dashboard: 'mdi-view-dashboard-outline',
  Students: 'mdi-account-school-outline',
  Fines: 'mdi-receipt-text-outline',
  Collections: 'mdi-cash-check',
  Collectibles: 'mdi-cash-clock',
  Funds: 'mdi-bank-outline',
  Disbursements: 'mdi-cash-minus',
  Attendance: 'mdi-calendar-check-outline',
  Reports: 'mdi-chart-box-outline',
  'Users & Roles': 'mdi-account-cog-outline',
  'External API': 'mdi-api',
  'Audit Logs': 'mdi-text-box-search-outline',
  'My Account': 'mdi-account-outline',
};
const pageLabel = value => pageLabels[value] || value;
const roleLabel = value => roleLabels[value] || value;
const reportLabel = value => ({
  'Fines & Collections': 'Student fines and payments',
  'Unpaid Balances': 'Unpaid balances',
  'Funds & Disbursements': 'Department funds and expenses',
  'Attendance per Event': 'Attendance by activity',
})[value] || value;
const typeLabel = value => ({
  DISBURSEMENT: 'Expense',
  OPENING_BALANCE: 'Opening balance',
  ADDITION: 'Fund addition',
  PAYMENT: 'Payment',
  FINE: 'Fine',
})[value] || value;
const displayCode = value => String(value || '')
  .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  .replaceAll('_', ' ')
  .toLowerCase()
  .replace(/\b[a-z]/g, letter => letter.toUpperCase());
const auditActionLabel = action => ({
  STUDENT_CREATED: 'Student added',
  STUDENT_UPDATED: 'Student details updated',
  LEDGER_ENTRY_CREATED: 'Fine or payment recorded',
  LEDGER_ENTRY_REVERSED: 'Financial correction recorded',
  FUND_ENTRY_CREATED: 'Fund entry recorded',
  FUND_ENTRY_REVERSED: 'Fund correction recorded',
  ATTENDANCE_EVENT_CREATED: 'Attendance activity created',
  ATTENDANCE_RECORDED: 'Attendance recorded',
  ATTENDANCE_STATUS_UPDATED: 'Attendance status updated',
  STUDENT_ACCOUNT_LINKED: 'Student account linked',
  STUDENT_ACCOUNT_UNLINKED: 'Student account unlinked',
  USER_CREATED: 'User account created',
  USER_UPDATED: 'User account updated',
  USER_ACTIVATED: 'User account activated',
  USER_DEACTIVATED: 'User account deactivated',
})[action] || displayCode(action);
const auditEntityLabel = entityType => ({
  LedgerEntry: 'Fine or payment',
  FundEntry: 'Fund entry',
  DepartmentEvent: 'Attendance activity',
  Attendance: 'Attendance record',
  UserAccount: 'User account',
})[entityType] || displayCode(entityType);

const menu = computed(() => role.value === 'ADMIN'
  ? ['Dashboard', 'Students', 'Fines', 'Collections', 'Collectibles', 'Funds', 'Disbursements', 'Attendance', 'Reports', 'External API', 'Users & Roles', 'Audit Logs']
  : role.value === 'TREASURER'
    ? ['Dashboard', 'Students', 'Fines', 'Collections', 'Collectibles', 'Funds', 'Disbursements', 'Reports']
    : role.value === 'OFFICER'
      ? ['Dashboard', 'Students', 'Attendance', 'Reports']
      : ['My Account']);
const financeRows = computed(() => ledger.value.filter(item => page.value === 'Fines'
  ? item.kind === 'FINE'
  : page.value === 'Collections'
    ? item.kind === 'PAYMENT'
    : true));
const unpaidStudents = computed(() => students.value.filter(student => balance(student.id) > 0));
const visibleFunds = computed(() => funds.value.filter(item => page.value === 'Funds' || item.type === 'DISBURSEMENT'));
const studentOptions = computed(() => students.value.map(student => ({
  title: `${student.fullName} (${student.studentNumber})`,
  value: student.id,
})));
const studentLinkOptions = computed(() => students.value.map(student => ({
  title: `${student.studentNumber} — ${student.fullName}`,
  value: student.id,
})));
const chartMaximum = computed(() => Math.max(1, ...(stats.value.collectionChart || []).map(item => Number(item.amount))));
const hasCollectionActivity = computed(() =>
  (stats.value.collectionChart || []).some(item => Number(item.amount) !== 0));
const reportOptions = computed(() => {
  const options = [{ title: 'Students', value: 'Students' }];
  if (['ADMIN', 'TREASURER'].includes(role.value)) {
    options.push(
      { title: 'Student fines and payments', value: 'Fines & Collections' },
      { title: 'Unpaid balances', value: 'Unpaid Balances' },
      { title: 'Department funds and expenses', value: 'Funds & Disbursements' },
    );
  }
  if (['ADMIN', 'OFFICER'].includes(role.value)) {
    options.push({ title: 'Attendance by activity', value: 'Attendance per Event' });
  }
  return options;
});
const dashboardCards = computed(() => {
  const cards = [];
  if (['ADMIN', 'TREASURER'].includes(role.value)) {
    cards.push(
      { title: 'Total payments collected', value: money(stats.value.collections), icon: 'mdi-cash-check' },
      { title: 'Fund balance', value: money(stats.value.fundBalance), icon: 'mdi-bank-outline' },
      { title: 'Unpaid balances', value: money(stats.value.unpaid), icon: 'mdi-cash-clock' },
    );
  }
  if (['ADMIN', 'OFFICER'].includes(role.value)) {
    cards.push({
      title: 'Attendance rate',
      value: `${stats.value.attendanceRate || 0}%`,
      icon: 'mdi-calendar-check-outline',
    });
  }
  return cards;
});
const statuses = ['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'];
const attendanceStatusOptions = statuses.map(value => ({
  title: value.charAt(0) + value.slice(1).toLowerCase(),
  value,
}));
const roleOptions = Object.entries(roleLabels).map(([value, title]) => ({ title, value }));
const fundTypeOptions = [
  { title: 'Opening balance', value: 'OPENING_BALANCE' },
  { title: 'Fund addition', value: 'ADDITION' },
];

function friendlyError(failure) {
  const message = failure.response?.data?.message
    || failure.response?.data?.detail
    || failure.response?.data?.error;
  const knownMessages = {
    'Payment exceeds unpaid balance.': "This payment is more than the student's unpaid balance. Check the balance and enter a smaller amount.",
    'Transaction reference already exists.': 'This reference number is already in use. Enter a different reference number.',
    'Idempotency key was already used with different transaction details.': 'This retry key belongs to a different transaction. Review the details and submit again.',
    'A record with the same unique value already exists.': 'A duplicate request or reference was detected. Refresh and verify the transaction before retrying.',
    'A reversal cannot itself be reversed.': 'A correction entry cannot be corrected again.',
    'This transaction has already been reversed.': 'This transaction already has a correction entry.',
    'The fine cannot be reversed because part of it has already been paid.': 'This fine cannot be corrected because part of it has already been paid.',
    'Insufficient fund balance.': 'The department fund balance is too low to record this expense.',
    'This fund entry has already been reversed.': 'This fund entry already has a correction entry.',
    'The fund entry cannot be reversed because those funds have already been spent.': 'This fund entry cannot be corrected because those funds have already been spent.',
    'Invalid fund entry.': 'Check the entry type, amount, and description. The amount must be greater than zero and have no more than two decimal places.',
    'Event inactive.': 'This attendance activity is inactive. Choose an active activity.',
    'Event not found.': 'This attendance activity could not be found. Refresh the page and try again.',
    'Already checked in.': 'This student already has an attendance record for this activity.',
    'Student not found.': 'This student could not be found. Check the student ID and try again.',
    'Username already exists.': 'This username is already in use. Choose a different username.',
    'You cannot remove your own administrator role.': 'You cannot change your own role from Administrator.',
    'You cannot deactivate your own account.': 'You cannot deactivate your own account.',
    'The last active administrator cannot be deactivated.': 'You cannot deactivate the last active administrator account.',
  };
  if (message && knownMessages[message]) return knownMessages[message];
  if (failure.response?.status === 403) return 'You do not have permission to do this.';
  if (!failure.response) return 'Could not connect to the department system. Check your connection and try again.';
  return message || 'Something went wrong. Refresh the page and try again.';
}

function auditDetails(item) {
  const details = String(item.details || '');
  const ledgerEntry = details.match(/^(FINE|PAYMENT) amount=([^;]+);reference=(.*)$/);
  if (item.action === 'LEDGER_ENTRY_CREATED' && ledgerEntry) {
    return `${typeLabel(ledgerEntry[1])} of ${money(ledgerEntry[2])}; reference ${ledgerEntry[3]}`;
  }
  const fundEntry = details.match(/^(OPENING_BALANCE|ADDITION|DISBURSEMENT) amount=(.+)$/);
  if (item.action === 'FUND_ENTRY_CREATED' && fundEntry) {
    return `${typeLabel(fundEntry[1])} of ${money(fundEntry[2])}`;
  }
  if (item.action === 'ATTENDANCE_EVENT_CREATED') {
    return details.replace(/^title=/, 'Activity: ');
  }
  if (item.action === 'STUDENT_CREATED' || item.action === 'STUDENT_UPDATED') {
    return details.replace(/^studentNumber=/, 'Student ID: ');
  }
  if (item.action === 'STUDENT_ACCOUNT_LINKED') {
    return details.replace(/^studentNumber=/, 'Student ID: ');
  }
  if (item.action === 'STUDENT_ACCOUNT_UNLINKED') return 'Student record unlinked';
  if (item.action.startsWith('USER_')) {
    return details.split(';').map(part => {
      const [key, ...rest] = part.split('=');
      const value = rest.join('=');
      if (key === 'username') return `Username: ${value}`;
      if (key === 'role') return `Role: ${roleLabel(value)}`;
      if (key === 'enabled') return `Account: ${value === 'true' ? 'Active' : 'Inactive'}`;
      return part;
    }).join('; ');
  }
  if (item.action === 'ATTENDANCE_RECORDED' || item.action === 'ATTENDANCE_STATUS_UPDATED') {
    return details.split(';').map(part => {
      const [key, ...rest] = part.split('=');
      const value = rest.join('=');
      if (key === 'eventId') return `Activity record #${value}`;
      if (key === 'studentId') return `Student record #${value}`;
      if (key === 'status') return `Status: ${value.toLowerCase().replace(/^\w/, letter => letter.toUpperCase())}`;
      return part;
    }).join('; ');
  }
  if (item.action === 'LEDGER_ENTRY_REVERSED' || item.action === 'FUND_ENTRY_REVERSED') {
    return details.split(';').map(part => {
      const [key, ...rest] = part.split('=');
      const value = rest.join('=');
      if (key === 'amount') return `Correction amount: ${money(value)}`;
      if (key === 'reason') return `Reason: ${value}`;
      if (key === 'reversalId') return `Correction record #${value}`;
      return part;
    }).join('; ');
  }
  return details;
}

const credentials = () => ({ headers: { Authorization: `Basic ${auth.value}` } });
async function request(method, url, data) {
  if (activeRequests === 0) error.value = '';
  activeRequests += 1;
  try {
    const response = await api({ method, url, data, ...credentials() });
    return response.data;
  } catch (failure) {
    setRequestFailure(error, notice, failure, friendlyError);
    throw failure;
  } finally {
    activeRequests -= 1;
  }
}

async function unlinkStudentRecord() {
  if (!editingUser.value || !editingUser.value.studentId) return;
  try {
    const updatedUser = await request(
      'put',
      `/admin/users/${editingUser.value.id}/student-link`,
      { studentId: null },
    );
    editingUser.value = updatedUser;
    userForm.value.studentId = null;
    notice.value = 'Student record unlinked from the account.';
    await loadAdminPage('Users & Roles');
  } catch {
    // Keep the dialog open so the link can be reviewed.
  }
}

async function login() {
  loading.value = true;
  error.value = '';
  notice.value = '';
  try {
    auth.value = btoa(`${username.value}:${password.value}`);
    const current = await request('get', '/me');
    role.value = current.roles[0]?.replace('ROLE_', '') || '';
    password.value = '';
    await refresh();
  } catch (failure) {
    if (failure.response?.status === 401) {
      error.value = 'Username or password is incorrect. Try again.';
    }
    auth.value = '';
    role.value = '';
  } finally {
    loading.value = false;
  }
}

async function logout() {
  await stopQrScanner();
  auth.value = '';
  role.value = '';
  error.value = '';
  notice.value = '';
  page.value = 'Dashboard';
  students.value = [];
  ledger.value = [];
  funds.value = [];
  events.value = [];
  records.value = [];
  users.value = [];
  auditLogs.value = [];
  stats.value = {};
  studentPortal.value = { summary: null, ledger: [], attendance: [] };
  reportData.value = { students: [], finance: null, funds: [], attendance: [] };
  externalApiCredential.value = '';
  externalApiResult.value = null;
  externalApiStatus.value = null;
  externalApiError.value = '';
}

async function refresh() {
  if (!auth.value) return;
  try {
    ledger.value = [];
    funds.value = [];
    events.value = [];
    records.value = [];
    reportData.value = { students: [], finance: null, funds: [], attendance: [] };
    if (role.value === 'STUDENT') {
      const [summary, ledgerRows, attendanceRows] = await Promise.all([
        request('get', '/students/me/summary'),
        request('get', '/students/me/ledger'),
        request('get', '/students/me/attendance'),
      ]);
      studentPortal.value = { summary, ledger: ledgerRows, attendance: attendanceRows };
      page.value = 'My Account';
      return;
    }
    studentPortal.value = { summary: null, ledger: [], attendance: [] };
    const [studentRows, dashboard] = await Promise.all([
      request('get', '/students'),
      request('get', '/dashboard'),
    ]);
    students.value = studentRows;
    stats.value = dashboard;
    if (['ADMIN', 'TREASURER'].includes(role.value)) {
      [ledger.value, funds.value] = await Promise.all([
        request('get', '/finance/ledger'),
        request('get', '/finance/funds'),
      ]);
    }
    if (['ADMIN', 'OFFICER'].includes(role.value)) {
      [events.value, records.value] = await Promise.all([
        request('get', '/attendance/events'),
        request('get', '/attendance/records'),
      ]);
    }
  } catch {
    // The request helper has already surfaced the error in the page alert.
  }
}

async function loadExternalApi() {
  externalApiLoading.value = true;
  externalApiError.value = '';
  externalApiResult.value = null;
  externalApiStatus.value = null;
  const credential = externalApiCredential.value;
  externalApiCredential.value = '';
  try {
    const requestBody = buildExternalApiRequest(
      externalApiUrl.value,
      externalApiAuthenticationType.value,
      credential,
    );
    const response = await request('post', '/admin/external-api/fetch', requestBody);
    externalApiStatus.value = response.status;
    externalApiResult.value = response.data;
  } catch (failure) {
    externalApiError.value = failure.response?.data?.detail
      || failure.message
      || 'The external API request failed.';
  } finally {
    externalApiLoading.value = false;
  }
}

async function loadStudents(search = '') {
  try {
    students.value = await request('get', `/students?search=${encodeURIComponent(search || '')}`);
  } catch {
    // The request helper has already surfaced the error in the page alert.
  }
}

function openStudentDialog(student = null) {
  editingStudent.value = student;
  studentForm.value = student
    ? { studentNumber: student.studentNumber, fullName: student.fullName, course: student.course || '', yearLevel: student.yearLevel || '', section: student.section || '' }
    : { studentNumber: '', fullName: '', course: '', yearLevel: '', section: '' };
  studentDialog.value = true;
}

async function saveStudent() {
  try {
    const url = editingStudent.value ? `/students/${editingStudent.value.id}` : '/students';
    await request(editingStudent.value ? 'put' : 'post', url, studentForm.value);
    studentDialog.value = false;
    notice.value = editingStudent.value ? 'Student details updated.' : 'Student added.';
    await loadStudents(studentSearch.value);
  } catch {
    // Keep the dialog open so validation errors can be corrected.
  }
}

async function submitLedger() {
  if (ledgerSubmitting.value) return;
  ledgerSubmitting.value = true;
  const kind = page.value === 'Fines' ? 'FINE' : 'PAYMENT';
  try {
    const details = { ...entry.value, kind };
    const storageKey = `track2-idempotency-ledger-${username.value}`;
    const idempotencyKey = await getIdempotencyKey(localStorage, storageKey, details);
    await request('post', '/finance/ledger', { ...details, idempotencyKey });
    clearIdempotencyKey(localStorage, storageKey, idempotencyKey);
    entry.value = { studentId: null, amount: null, description: '', referenceNumber: '' };
    await refresh();
    notice.value = kind === 'FINE' ? 'Fine recorded.' : 'Payment recorded.';
  } catch (failure) {
    if (!failure.response && !error.value) {
      error.value = failure.message || 'Secure transaction retry protection is unavailable.';
    }
  } finally {
    ledgerSubmitting.value = false;
  }
}

async function submitFund() {
  if (fundSubmitting.value) return;
  fundSubmitting.value = true;
  const type = page.value === 'Disbursements' ? 'DISBURSEMENT' : fund.value.type;
  try {
    const details = { ...fund.value, type };
    const storageKey = `track2-idempotency-fund-${username.value}`;
    const idempotencyKey = await getIdempotencyKey(localStorage, storageKey, details);
    await request('post', '/finance/funds', { ...details, idempotencyKey });
    clearIdempotencyKey(localStorage, storageKey, idempotencyKey);
    fund.value = { type: 'ADDITION', amount: null, description: '' };
    await refresh();
    notice.value = type === 'DISBURSEMENT' ? 'Expense recorded.' : 'Fund entry recorded.';
  } catch (failure) {
    if (!failure.response && !error.value) {
      error.value = failure.message || 'Secure transaction retry protection is unavailable.';
    }
  } finally {
    fundSubmitting.value = false;
  }
}

async function createEvent() {
  try {
    await request('post', '/attendance/events', eventForm.value);
    eventForm.value = { title: '', eventDate: new Date().toISOString().slice(0, 10), active: true };
    notice.value = 'Attendance activity created.';
    await refresh();
  } catch (failure) {
    if (!failure.response && !error.value) {
      error.value = failure.message || 'Secure transaction retry protection is unavailable.';
    }
  }
}

async function markAttendance() {
  try {
    await request('post', '/attendance/records', scan.value);
    scan.value = { ...scan.value, studentNumber: '' };
    notice.value = 'Attendance recorded.';
    await refresh();
  } catch {
    // Keep entered values after a rejected request.
  }
}

async function updateAttendance(record, status) {
  try {
    await request('put', `/attendance/records/${record.id}`, { status });
    notice.value = 'Attendance status updated.';
    await refresh();
  } catch {
    await refresh();
  }
}

async function startQrScanner() {
  scannerElement.value = true;
  await nextTick();
  try {
    const { Html5Qrcode } = await import('html5-qrcode');
    qrScanner.value = new Html5Qrcode('qr-reader');
    await qrScanner.value.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 240, height: 240 } },
      async decodedText => {
        scan.value.studentNumber = decodedText.trim();
        await stopQrScanner();
        notice.value = 'QR read. Confirm the event and attendance status, then record attendance.';
      },
      () => {},
    );
  } catch (failure) {
    error.value = failure.message || 'Could not start the camera scanner.';
    await stopQrScanner();
  }
}

async function stopQrScanner() {
  if (qrScanner.value) {
    try {
      if (qrScanner.value.isScanning) await qrScanner.value.stop();
      qrScanner.value.clear();
    } catch {
      // The scanner may already have stopped after a successful scan.
    }
    qrScanner.value = null;
  }
  scannerElement.value = false;
}

function openUserDialog(user = null) {
  editingUser.value = user;
  userForm.value = user
    ? { username: user.username, role: user.role, enabled: user.enabled, password: '', newPassword: '', studentId: user.studentId ?? null }
    : { username: '', role: 'STUDENT', enabled: true, password: '', newPassword: '', studentId: null };
  userDialog.value = true;
}

async function loadAdminPage(value) {
  if (!auth.value || role.value !== 'ADMIN') return;
  try {
    if (value === 'Users & Roles') users.value = await request('get', '/admin/users');
    if (value === 'Audit Logs') auditLogs.value = await request('get', '/admin/audit-logs');
  } catch {
    // The request helper has already surfaced the error in the page alert.
  }
}

async function saveUser() {
  try {
    const wasEditing = await saveUserAccount({ editingUser, userForm, request });
    userDialog.value = false;
    notice.value = wasEditing ? 'User account updated.' : 'User account created.';
    await loadAdminPage('Users & Roles');
  } catch {
    // Keep the dialog open so validation errors can be corrected.
  }
}

async function toggleUser(user) {
  try {
    await request('put', `/admin/users/${user.id}`, {
      username: user.username,
      role: user.role,
      enabled: !user.enabled,
    });
    notice.value = 'User status updated.';
    await loadAdminPage('Users & Roles');
  } catch {
    // The request helper has already surfaced the error in the page alert.
  }
}

function openReverseDialog(item, type) {
  reverseTarget.value = item;
  reverseType.value = type;
  reverseReason.value = '';
  reverseDialog.value = true;
}

function isReversed(item, type) {
  const originalType = type === 'ledger' ? item.kind : item.type;
  const prefix = `REVERSAL:${originalType}:${item.id}:`;
  return (type === 'ledger' ? ledger.value : funds.value)
    .some(record => record.description.startsWith(prefix));
}

async function reverseTransaction() {
  if (!reverseTarget.value) return;
  const route = reverseType.value === 'ledger' ? '/finance/ledger' : '/finance/funds';
  const storageKey = `track2-idempotency-reversal-${username.value}-${reverseType.value}-${reverseTarget.value.id}`;
  try {
    const details = { reason: reverseReason.value.trim() };
    const idempotencyKey = await getIdempotencyKey(localStorage, storageKey, details);
    await request('post', `${route}/${reverseTarget.value.id}/reverse`, { ...details, idempotencyKey });
    clearIdempotencyKey(localStorage, storageKey, idempotencyKey);
    reverseDialog.value = false;
    notice.value = 'A correction entry was added. The original transaction remains recorded.';
    await refresh();
  } catch (failure) {
    if (!failure.response && !error.value) {
      error.value = failure.message || 'Secure transaction retry protection is unavailable.';
    }
  }
}

async function loadReports() {
  if (!['ADMIN', 'TREASURER', 'OFFICER'].includes(role.value)) return;
  if (!reportOptions.value.some(option => option.value === reportKind.value)) {
    reportKind.value = 'Students';
  }
  try {
    const [studentRows, finance, fundRows, attendanceRows] = await Promise.all([
      request('get', '/reports/students'),
      ['ADMIN', 'TREASURER'].includes(role.value) ? request('get', '/reports/finance') : Promise.resolve(null),
      ['ADMIN', 'TREASURER'].includes(role.value) ? request('get', '/reports/funds') : Promise.resolve([]),
      ['ADMIN', 'OFFICER'].includes(role.value) ? request('get', '/reports/attendance') : Promise.resolve([]),
    ]);
    reportData.value = {
      students: studentRows,
      finance,
      funds: fundRows,
      attendance: attendanceRows,
    };
  } catch {
    // The request helper has already surfaced the error in the page alert.
  }
}

function reportRows() {
  if (reportKind.value === 'Students') {
    return reportData.value.students.map(({ studentNumber, fullName, course, yearLevel, section }) =>
      ({ studentNumber, fullName, course, yearLevel, section }));
  }
  if (reportKind.value === 'Fines & Collections') {
    return (reportData.value.finance?.transactions || []).map(item => ({
      date: item.createdAt,
      student: item.student?.studentNumber,
      kind: item.kind,
      amount: Number(item.amount).toFixed(2),
      reference: item.referenceNumber,
      description: item.description,
    }));
  }
  if (reportKind.value === 'Unpaid Balances') {
    return (reportData.value.finance?.balances || []).map(({ studentNumber, fullName, unpaidBalance }) =>
      ({ studentNumber, fullName, unpaidBalance: Number(unpaidBalance).toFixed(2) }));
  }
  if (reportKind.value === 'Funds & Disbursements') {
    return reportData.value.funds.map(({ createdAt, type, amount, description }) =>
      ({ createdAt, type, amount: Number(amount).toFixed(2), description }));
  }
  if (reportKind.value === 'Attendance per Event') {
    return reportData.value.attendance.map(({ eventDate, title, present, late, absent, excused, total }) =>
      ({ eventDate, title, present, late, absent, excused, total }));
  }
  return [];
}

function exportExcelCsv() {
  const rows = reportRows();
  if (!rows.length) {
    notice.value = 'There is no data in this report to download.';
    return;
  }
  const columns = Object.keys(rows[0]);
  const escapeCsv = value => {
    const text = String(value ?? '');
    const safeText = /^[\t\r\n]/.test(text) || /^[\u0000-\u0020]*[=+\-@]/.test(text)
      ? `'${text}`
      : text;
    return `"${safeText.replaceAll('"', '""')}"`;
  };
  const csv = [columns.map(escapeCsv).join(','), ...rows.map(row => columns.map(key => escapeCsv(row[key])).join(','))]
    .join('\r\n');
  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${reportKind.value.toLowerCase().replaceAll(/[^a-z0-9]+/g, '-')}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

const printReport = () => window.print();
const money = value => `₱${Number(value || 0).toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const balance = id => ledger.value
  .filter(item => item.student?.id === id)
  .reduce((total, item) => total + (item.kind === 'FINE' ? Number(item.amount) : -Number(item.amount)), 0);

watch(page, async value => {
  if (scannerElement.value && value !== 'Attendance') await stopQrScanner();
  if (value === 'Users & Roles' || value === 'Audit Logs') await loadAdminPage(value);
  if (value === 'Reports') await loadReports();
});

onBeforeUnmount(() => {
  void stopQrScanner();
});
</script>

<template>
  <v-app>
    <template v-if="!role">
      <v-main class="login-page">
        <v-card class="login-card">
          <section class="login-brand">
            <v-avatar color="white" rounded="lg" size="54">
              <v-icon icon="mdi-bank-outline" color="primary" size="30" />
            </v-avatar>
            <div class="login-brand-copy">
              <p class="login-eyebrow">TRACK 2</p>
              <h1>Department Management</h1>
              <p>One place to manage students, payments, funds, and attendance.</p>
            </div>
            <div class="login-brand-footer">Department Finance System</div>
          </section>
          <section class="login-content">
            <div class="mb-7">
              <h2>Sign in</h2>
              <p class="muted mt-2">Use your department account to continue.</p>
            </div>
            <v-form @submit.prevent="login">
              <v-text-field v-model="username" label="Username" variant="outlined" autocomplete="username"
                prepend-inner-icon="mdi-account-outline" required />
              <v-text-field v-model="password" type="password" label="Password" variant="outlined"
                autocomplete="current-password" prepend-inner-icon="mdi-lock-outline" required />
              <v-alert v-if="error" type="error" density="comfortable" class="mb-4">{{ error }}</v-alert>
              <v-btn type="submit" block color="primary" size="large" :loading="loading">Sign in</v-btn>
            </v-form>
            <div class="login-help muted">Need an account? Ask your administrator for help.</div>
          </section>
        </v-card>
      </v-main>
    </template>

    <template v-else>
      <v-navigation-drawer v-if="role !== 'STUDENT'"
        :model-value="mdAndUp || drawerOpen"
        :permanent="mdAndUp"
        :temporary="!mdAndUp"
        width="256"
        class="app-drawer"
        @update:model-value="drawerOpen = $event">
        <div class="brand-lockup">
          <v-avatar color="primary" rounded="lg" size="38">
            <v-icon icon="mdi-bank-outline" color="white" size="22" />
          </v-avatar>
          <div>
            <div class="brand-title">Track 2</div>
            <div class="brand-caption">Department system</div>
          </div>
        </div>
        <v-divider />
        <div class="nav-section-label">MENU</div>
        <v-list nav class="app-navigation">
          <v-list-item v-for="item in menu" :key="item" :title="pageLabel(item)"
            :prepend-icon="navigationIcons[item]" :active="page === item" color="primary"
            rounded="lg" @click="page = item; drawerOpen = false" />
        </v-list>
      </v-navigation-drawer>

      <v-main class="app-main">
        <div class="content-wrap">
          <div class="page-heading">
            <div>
              <div class="page-kicker">DEPARTMENT SERVICES</div>
              <h1 class="page-title">{{ pageLabel(page) }}</h1>
            </div>
            <div class="page-heading-actions">
              <v-btn v-if="role !== 'STUDENT' && !mdAndUp" icon="mdi-menu" variant="text"
                aria-label="Open navigation" @click="drawerOpen = !drawerOpen" />
              <v-menu location="bottom end">
                <template #activator="{ props }">
                  <v-btn v-bind="props" variant="text" class="profile-button" aria-label="Open profile menu">
                    <v-avatar color="primary" size="36"><v-icon icon="mdi-account" color="white" /></v-avatar>
                    <span class="profile-role">{{ roleLabel(role) }}</span>
                    <v-icon icon="mdi-chevron-down" size="18" />
                  </v-btn>
                </template>
                <v-list class="profile-menu" min-width="220">
                  <v-list-item :title="username" :subtitle="roleLabel(role)" prepend-icon="mdi-account-circle-outline" />
                  <v-divider class="my-1" />
                  <v-list-item title="Log out" prepend-icon="mdi-logout" @click="logout" />
                </v-list>
              </v-menu>
              <v-btn v-if="role !== 'STUDENT'" prepend-icon="mdi-refresh" variant="outlined"
                color="primary" @click="refresh">Refresh</v-btn>
            </div>
          </div>
          <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = ''">{{ error }}</v-alert>
          <v-alert v-if="notice" type="success" closable class="mb-4" @click:close="notice = ''">{{ notice }}</v-alert>

          <template v-if="page === 'Dashboard'">
            <v-row>
              <v-col v-for="card in dashboardCards" :key="card.title" cols="12" sm="6" md="3">
                <v-card class="metric-card pa-5">
                  <div class="metric-heading"><span>{{ card.title }}</span><v-icon :icon="card.icon" color="primary" size="21" /></div>
                  <div class="metric-value">{{ card.value }}</div>
                  <p v-if="card.title === 'Attendance rate'" class="muted text-caption mt-2">Share of possible check-ins for active activities marked Present or Late. Total scans: {{ stats.attendanceScans || 0 }}.</p>
                </v-card>
              </v-col>
            </v-row>
            <v-row v-if="['ADMIN', 'TREASURER'].includes(role)" class="mt-1">
              <v-col cols="12" md="6">
                <v-card class="pa-5 h-100">
                  <h3>Payments collected by month</h3>
                  <template v-if="hasCollectionActivity">
                    <div v-for="month in stats.collectionChart || []" :key="month.month" class="chart-row">
                      <span class="chart-label">{{ month.month }}</span>
                      <div class="chart-track"><div class="chart-bar" :style="{ width: `${Math.max(0, Number(month.amount) / chartMaximum * 100)}%` }" /></div>
                      <span class="chart-value">{{ money(month.amount) }}</span>
                    </div>
                  </template>
                  <p v-else class="muted mt-4">No payment activity for this period.</p>
                </v-card>
              </v-col>
              <v-col cols="12" md="6">
                <v-card class="pa-5 h-100">
                  <h3>Recent payments and fines</h3>
                  <div class="scroll-table"><v-table density="compact">
                    <thead><tr><th>Date</th><th>Student</th><th>Record type</th><th>Amount</th></tr></thead>
                    <tbody><tr v-for="item in stats.recent || []" :key="item.id">
                      <td>{{ item.createdAt?.slice(0, 10) }}</td><td>{{ item.student?.fullName }}</td>
                      <td>{{ typeLabel(item.kind) }}</td><td>{{ money(item.amount) }}</td>
                    </tr><tr v-if="!stats.recent?.length"><td colspan="4" class="text-center muted py-6">No recent payments or fines.</td></tr></tbody>
                  </v-table></div>
                </v-card>
              </v-col>
            </v-row>
          </template>

          <template v-else-if="page === 'Students'">
            <v-card class="pa-4 mb-4">
              <div class="d-flex flex-wrap align-center ga-3">
                <v-text-field v-model="studentSearch" label="Search by student ID or student name" prepend-inner-icon="mdi-magnify"
                  variant="outlined" density="compact" hide-details clearable @update:model-value="loadStudents" />
                <v-btn v-if="role === 'ADMIN'" color="primary" @click="openStudentDialog()">Add student</v-btn>
              </div>
            </v-card>
            <v-card class="pa-4 scroll-table">
              <v-table density="compact">
                <thead><tr><th>Student ID</th><th>Full name</th><th>Course</th><th>Year level</th><th>Section</th><th v-if="role === 'ADMIN'">Actions</th></tr></thead>
                <tbody><tr v-for="student in students" :key="student.id">
                  <td>{{ student.studentNumber }}</td><td>{{ student.fullName }}</td><td>{{ student.course }}</td>
                  <td>{{ student.yearLevel }}</td><td>{{ student.section }}</td>
                  <td v-if="role === 'ADMIN'"><v-btn size="small" variant="text" color="primary" @click="openStudentDialog(student)">Edit</v-btn></td>
                </tr><tr v-if="!students.length"><td :colspan="role === 'ADMIN' ? 6 : 5" class="text-center muted py-6">{{ studentSearch ? 'No students match your search.' : 'No students have been added yet.' }}</td></tr></tbody>
              </v-table>
            </v-card>
            <v-dialog v-model="studentDialog" max-width="650">
              <v-card class="pa-5">
                <h3>{{ editingStudent ? 'Edit student' : 'Add student' }}</h3>
                <v-form class="mt-4" @submit.prevent="saveStudent">
                  <v-row>
                    <v-col cols="12" sm="6"><v-text-field v-model="studentForm.studentNumber" label="Student ID" maxlength="255" required variant="outlined" /></v-col>
                    <v-col cols="12" sm="6"><v-text-field v-model="studentForm.fullName" label="Full name" maxlength="255" required variant="outlined" /></v-col>
                    <v-col cols="12" sm="4"><v-text-field v-model="studentForm.course" label="Course" variant="outlined" /></v-col>
                    <v-col cols="12" sm="4"><v-text-field v-model="studentForm.yearLevel" label="Year level" variant="outlined" /></v-col>
                    <v-col cols="12" sm="4"><v-text-field v-model="studentForm.section" label="Section" variant="outlined" /></v-col>
                  </v-row>
                  <div class="d-flex justify-end ga-2"><v-btn variant="text" @click="studentDialog = false">Cancel</v-btn><v-btn type="submit" color="primary">{{ editingStudent ? 'Save changes' : 'Add student' }}</v-btn></div>
                </v-form>
              </v-card>
            </v-dialog>
          </template>

          <template v-else-if="['Fines', 'Collections', 'Collectibles'].includes(page)">
            <v-card v-if="page !== 'Collectibles' && ['ADMIN', 'TREASURER'].includes(role)" class="pa-5 mb-4">
              <h3 class="mb-3">{{ page === 'Fines' ? 'Record student fine' : 'Record payment' }}</h3>
              <v-form @submit.prevent="submitLedger">
                <v-row>
                  <v-col cols="12" md="3"><v-select v-model="entry.studentId" :items="studentOptions" label="Student" variant="outlined" density="compact" required /></v-col>
                  <v-col cols="12" md="2"><v-text-field v-model.number="entry.amount" type="number" min="0.01" step="0.01" label="Amount (₱)" variant="outlined" density="compact" required /></v-col>
                  <v-col cols="12" md="3"><v-text-field v-model="entry.description" :label="page === 'Fines' ? 'Reason for fine' : 'Payment description'" maxlength="255" variant="outlined" density="compact" required /></v-col>
                  <v-col cols="12" md="2"><v-text-field v-model="entry.referenceNumber" label="Reference number (optional)" maxlength="255" variant="outlined" density="compact" /></v-col>
                  <v-col cols="12" md="2"><v-btn type="submit" color="primary" :loading="ledgerSubmitting" :disabled="ledgerSubmitting">{{ page === 'Fines' ? 'Record fine' : 'Record payment' }}</v-btn></v-col>
                </v-row>
                <div v-if="page === 'Collections'" class="muted text-caption">You can record a partial payment, but it cannot be more than the student's unpaid balance.</div>
              </v-form>
            </v-card>
            <v-card class="pa-4 scroll-table">
              <v-table density="compact">
                <thead v-if="page === 'Collectibles'"><tr><th>Student ID</th><th>Student Name</th><th>Unpaid Balance</th></tr></thead>
                <thead v-else><tr><th>Student</th><th>Record type</th><th>Amount</th><th>Description / Reference number</th><th v-if="['ADMIN', 'TREASURER'].includes(role)">Actions</th></tr></thead>
                <tbody>
                  <template v-if="page === 'Collectibles'">
                    <tr v-for="student in unpaidStudents" :key="student.id">
                      <td>{{ student.studentNumber }}</td><td>{{ student.fullName }}</td><td>{{ money(balance(student.id)) }}</td>
                    </tr>
                    <tr v-if="!unpaidStudents.length"><td colspan="3" class="text-center muted py-6">No unpaid balances to show.</td></tr>
                  </template>
                  <template v-else>
                    <tr v-for="item in financeRows" :key="item.id">
                      <td>{{ item.student?.fullName }}</td><td>{{ typeLabel(item.kind) }}</td><td>{{ money(item.amount) }}</td>
                      <td>{{ item.description }} <span class="muted">{{ item.referenceNumber }}</span></td>
                      <td v-if="['ADMIN', 'TREASURER'].includes(role)">
                        <v-btn v-if="!item.description.startsWith('REVERSAL:') && !isReversed(item, 'ledger')"
                          size="small" variant="text" color="error" @click="openReverseDialog(item, 'ledger')">Correct entry</v-btn>
                        <span v-else-if="isReversed(item, 'ledger')" class="muted">Correction recorded</span>
                      </td>
                    </tr>
                    <tr v-if="!financeRows.length"><td :colspan="['ADMIN', 'TREASURER'].includes(role) ? 5 : 4" class="text-center muted py-6">{{ page === 'Fines' ? 'No student fines recorded yet.' : 'No payments recorded yet.' }}</td></tr>
                  </template>
                </tbody>
              </v-table>
            </v-card>
          </template>

          <template v-else-if="['Funds', 'Disbursements'].includes(page)">
            <v-card class="pa-5 mb-4">
              <h3 class="mb-3">{{ page === 'Funds' ? 'Record department fund entry' : 'Record expense' }}</h3>
              <v-form @submit.prevent="submitFund">
                <v-row>
                  <v-col cols="12" md="3"><v-select v-if="page === 'Funds'" v-model="fund.type" label="Entry type" :items="fundTypeOptions" variant="outlined" density="compact" /></v-col>
                  <v-col cols="12" md="2"><v-text-field v-model.number="fund.amount" :label="page === 'Funds' ? 'Amount (₱)' : 'Expense amount (₱)'" type="number" min="0.01" step="0.01" variant="outlined" density="compact" required /></v-col>
                  <v-col cols="12" md="5"><v-text-field v-model="fund.description" :label="page === 'Funds' ? 'Description or payee' : 'Payee or reason'" maxlength="255" variant="outlined" density="compact" required /></v-col>
                  <v-col cols="12" md="2"><v-btn type="submit" color="primary" :loading="fundSubmitting" :disabled="fundSubmitting">{{ page === 'Funds' ? 'Record fund entry' : 'Record expense' }}</v-btn></v-col>
                </v-row>
              </v-form>
            </v-card>
            <v-card class="pa-4 scroll-table">
              <v-table density="compact">
                <thead><tr><th>Date</th><th>Type</th><th>Amount</th><th>Description</th><th>Actions</th></tr></thead>
                <tbody><tr v-for="item in visibleFunds" :key="item.id">
                  <td>{{ item.createdAt?.slice(0, 10) }}</td><td>{{ typeLabel(item.type) }}</td><td>{{ money(item.amount) }}</td><td>{{ item.description }}</td>
                  <td><v-btn v-if="!item.description.startsWith('REVERSAL:') && !isReversed(item, 'fund')" size="small" variant="text" color="error" @click="openReverseDialog(item, 'fund')">Correct entry</v-btn>
                    <span v-else-if="isReversed(item, 'fund')" class="muted">Correction recorded</span></td>
                </tr><tr v-if="!visibleFunds.length"><td colspan="5" class="text-center muted py-6">{{ page === 'Funds' ? 'No department fund entries yet.' : 'No expenses recorded yet.' }}</td></tr></tbody>
              </v-table>
            </v-card>
          </template>

          <template v-else-if="page === 'Attendance'">
            <v-card class="pa-5 mb-4">
              <h3>Create activity</h3>
              <v-form class="mt-3" @submit.prevent="createEvent">
                <v-row>
                  <v-col cols="12" md="5"><v-text-field v-model="eventForm.title" label="Activity name" maxlength="255" required variant="outlined" /></v-col>
                  <v-col cols="12" md="4"><v-text-field v-model="eventForm.eventDate" type="date" label="Date" required variant="outlined" /></v-col>
                  <v-col cols="12" md="3"><v-btn type="submit" color="primary">Create activity</v-btn></v-col>
                </v-row>
              </v-form>
            </v-card>
            <v-card class="pa-5 mb-4">
              <h3>Record attendance</h3>
              <v-form class="mt-3" @submit.prevent="markAttendance">
                <v-row>
                  <v-col cols="12" md="3"><v-select v-model="scan.eventId" :items="events.map(item => ({ title: `${item.title} - ${item.eventDate}`, value: item.id }))" label="Activity" required variant="outlined" /></v-col>
                  <v-col cols="12" md="3"><v-text-field v-model="scan.studentNumber" label="Student ID or scanned QR code" maxlength="255" required variant="outlined" /></v-col>
                  <v-col cols="12" md="2"><v-select v-model="scan.status" :items="attendanceStatusOptions" label="Status" variant="outlined" /></v-col>
                  <v-col cols="12" md="2"><v-btn type="submit" color="primary">Record attendance</v-btn></v-col>
                  <v-col cols="12" md="2"><v-btn variant="outlined" @click="scannerElement ? stopQrScanner() : startQrScanner()">{{ scannerElement ? 'Stop scanning' : 'Scan student QR code' }}</v-btn></v-col>
                </v-row>
              </v-form>
              <div v-if="scannerElement" id="qr-reader" class="qr-reader mt-3" />
              <div class="muted text-caption">Allow camera access to scan a QR code. The code must contain a student ID.</div>
            </v-card>
            <v-card class="pa-4 scroll-table">
              <v-table density="compact">
                <thead><tr><th>Student</th><th>Activity</th><th>Check-in time</th><th>Status</th></tr></thead>
                <tbody><tr v-for="record in records" :key="record.id">
                  <td>{{ record.student?.fullName }} ({{ record.student?.studentNumber }})</td>
                  <td>{{ record.event?.title }}</td><td>{{ record.scannedAt }}</td>
                  <td><v-select :model-value="record.status" :items="attendanceStatusOptions" density="compact" variant="underlined" hide-details
                    @update:model-value="updateAttendance(record, $event)" /></td>
                </tr><tr v-if="!records.length"><td colspan="4" class="text-center muted py-6">No attendance records yet.</td></tr></tbody>
              </v-table>
            </v-card>
          </template>

          <template v-else-if="page === 'Reports'">
            <v-card class="pa-4 mb-4 no-print">
              <div class="d-flex flex-wrap align-center ga-3">
                <v-select v-model="reportKind" :items="reportOptions" label="Report" variant="outlined" density="compact" hide-details />
                <v-btn variant="outlined" color="primary" @click="exportExcelCsv">Download CSV for Excel</v-btn>
                <v-btn color="primary" @click="printReport">Print or save as PDF</v-btn>
              </div>
            </v-card>
            <v-card class="pa-4 printable-report">
              <h2 class="mb-1">{{ reportLabel(reportKind) }}</h2><div class="muted mb-4">Department report · {{ new Date().toLocaleDateString() }}</div>
              <div class="scroll-table">
                <v-table density="compact">
                  <template v-if="reportKind === 'Students'">
                    <thead><tr><th>Student ID</th><th>Full name</th><th>Course</th><th>Year level</th><th>Section</th></tr></thead>
                    <tbody><tr v-for="item in reportData.students" :key="item.id"><td>{{ item.studentNumber }}</td><td>{{ item.fullName }}</td><td>{{ item.course }}</td><td>{{ item.yearLevel }}</td><td>{{ item.section }}</td></tr><tr v-if="!reportData.students.length"><td colspan="5" class="text-center muted py-6">No records for this report.</td></tr></tbody>
                  </template>
                  <template v-else-if="reportKind === 'Fines & Collections'">
                    <thead><tr><th>Date</th><th>Student</th><th>Type</th><th>Amount</th><th>Reference</th><th>Description</th></tr></thead>
                    <tbody><tr v-for="item in reportData.finance?.transactions || []" :key="item.id"><td>{{ item.createdAt?.slice(0, 10) }}</td><td>{{ item.student?.studentNumber }}</td><td>{{ typeLabel(item.kind) }}</td><td>{{ money(item.amount) }}</td><td>{{ item.referenceNumber }}</td><td>{{ item.description }}</td></tr><tr v-if="!reportData.finance?.transactions?.length"><td colspan="6" class="text-center muted py-6">No records for this report.</td></tr></tbody>
                  </template>
                  <template v-else-if="reportKind === 'Unpaid Balances'">
                    <thead><tr><th>Student ID</th><th>Student Name</th><th>Unpaid Balance</th></tr></thead>
                    <tbody><tr v-for="item in reportData.finance?.balances || []" :key="item.studentNumber"><td>{{ item.studentNumber }}</td><td>{{ item.fullName }}</td><td>{{ money(item.unpaidBalance) }}</td></tr><tr v-if="!reportData.finance?.balances?.length"><td colspan="3" class="text-center muted py-6">No unpaid balances to show.</td></tr></tbody>
                  </template>
                  <template v-else-if="reportKind === 'Funds & Disbursements'">
                    <thead><tr><th>Date</th><th>Type</th><th>Amount</th><th>Description</th></tr></thead>
                    <tbody><tr v-for="item in reportData.funds" :key="item.id"><td>{{ item.createdAt?.slice(0, 10) }}</td><td>{{ typeLabel(item.type) }}</td><td>{{ money(item.amount) }}</td><td>{{ item.description }}</td></tr><tr v-if="!reportData.funds.length"><td colspan="4" class="text-center muted py-6">No records for this report.</td></tr></tbody>
                  </template>
                  <template v-else>
                    <thead><tr><th>Date</th><th>Activity</th><th>Present</th><th>Late</th><th>Absent</th><th>Excused</th><th>Total</th></tr></thead>
                    <tbody><tr v-for="item in reportData.attendance" :key="item.eventId"><td>{{ item.eventDate }}</td><td>{{ item.title }}</td><td>{{ item.present }}</td><td>{{ item.late }}</td><td>{{ item.absent }}</td><td>{{ item.excused }}</td><td>{{ item.total }}</td></tr><tr v-if="!reportData.attendance.length"><td colspan="7" class="text-center muted py-6">No records for this report.</td></tr></tbody>
                  </template>
                </v-table>
              </div>
            </v-card>
          </template>

          <template v-else-if="page === 'External API' && role === 'ADMIN'">
            <v-card class="pa-5 mb-4">
              <h3>Read data from another system</h3>
              <p class="muted mt-2">
                Track 2 makes a read-only GET request through its backend to a configured trusted endpoint.
                Remote endpoints must use HTTPS. The local demonstration endpoint is loopback-only.
              </p>
              <v-alert type="info" variant="tonal" class="my-4">
                Requests are not imported, saved, synchronized, or used to change records.
                The credential is sent to Track 2 for this request, then forwarded to the configured endpoint.
                It is not saved.
              </v-alert>
              <v-form @submit.prevent="loadExternalApi">
                <v-text-field
                  v-model="externalApiUrl"
                  label="External API URL"
                  placeholder="https://trusted-api.example/v1/summary"
                  type="url"
                  autocomplete="url"
                  required
                  variant="outlined"
                  hint="Use an approved HTTPS endpoint. HTTP is allowed only for the local 127.0.0.1 demo."
                  persistent-hint
                />
                <v-select
                  v-model="externalApiAuthenticationType"
                  :items="[
                    { title: 'No authentication', value: 'NONE' },
                    { title: 'X-API-Key', value: 'API_KEY' },
                    { title: 'Bearer token', value: 'BEARER' },
                  ]"
                  label="Authentication"
                  variant="outlined"
                />
                <v-text-field
                  v-if="externalApiAuthenticationType !== 'NONE'"
                  v-model="externalApiCredential"
                  :label="externalApiAuthenticationType === 'API_KEY' ? 'External API key' : 'Bearer token'"
                  type="password"
                  autocomplete="new-password"
                  variant="outlined"
                />
                <v-btn type="submit" color="primary" :loading="externalApiLoading" :disabled="externalApiLoading">
                  Get JSON data
                </v-btn>
              </v-form>
            </v-card>
            <v-alert v-if="externalApiError" type="error" class="mb-4">
              {{ externalApiError }}
            </v-alert>
            <v-card v-if="externalApiResult !== null" class="pa-5">
              <h3 class="mb-3">External API response (upstream HTTP {{ externalApiStatus }})</h3>
              <v-alert
                :type="externalApiStatus >= 200 && externalApiStatus < 300 ? 'success' : 'error'"
                variant="tonal"
                class="mb-4"
              >
                The external endpoint returned HTTP {{ externalApiStatus }}.
              </v-alert>
              <pre class="external-api-json">{{ JSON.stringify(externalApiResult, null, 2) }}</pre>
            </v-card>
          </template>

          <template v-else-if="page === 'Users & Roles'">
            <v-card class="pa-5">
              <div class="d-flex align-center justify-space-between mb-3"><h3>User accounts</h3><v-btn color="primary" @click="openUserDialog()">Add user account</v-btn></div>
              <div class="scroll-table"><v-table density="compact">
                <thead><tr><th>Username</th><th>Role</th><th>Linked student</th><th>Status</th><th>Date added</th><th>Actions</th></tr></thead>
                <tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td>{{ roleLabel(user.role) }}</td>
                  <td>{{ user.studentNumber ? `${user.studentNumber} — ${user.studentName}` : 'Not linked' }}</td>
                  <td><v-chip size="small" :color="user.enabled ? 'success' : 'secondary'">{{ user.enabled ? 'Active' : 'Inactive' }}</v-chip></td>
                  <td>{{ user.createdAt?.slice(0, 10) }}</td>
                  <td><v-btn size="small" variant="text" color="primary" @click="openUserDialog(user)">Edit</v-btn>
                    <v-btn size="small" variant="text" :color="user.enabled ? 'error' : 'success'"
                      :disabled="user.username === username && user.enabled" @click="toggleUser(user)">{{ user.enabled ? 'Deactivate' : 'Activate' }}</v-btn></td>
                </tr><tr v-if="!users.length"><td colspan="6" class="text-center muted py-6">No user accounts have been added yet.</td></tr></tbody>
              </v-table></div>
            </v-card>
            <v-dialog v-model="userDialog" max-width="560"><v-card class="pa-5">
              <h3>{{ editingUser ? 'Edit user' : 'Create user' }}</h3>
              <v-form class="mt-4" @submit.prevent="saveUser">
                <v-text-field v-model="userForm.username" label="Username" maxlength="100" required variant="outlined" />
                <v-select v-model="userForm.role" label="Role" :items="roleOptions" required variant="outlined" />
                <v-select v-if="userForm.role === 'STUDENT'" v-model="userForm.studentId"
                  label="Linked student record (optional)" :items="studentLinkOptions" clearable variant="outlined" />
                <v-alert v-if="editingUser?.studentId && userForm.role !== 'STUDENT'" type="info" density="compact" class="mb-4">
                  Unlink this student record before changing the account to another role.
                  <v-btn variant="text" size="small" @click="unlinkStudentRecord">Unlink student record</v-btn>
                </v-alert>
                <v-text-field v-if="!editingUser" v-model="userForm.password" type="password" label="Temporary password (at least 12 characters)" minlength="12" required autocomplete="new-password" variant="outlined" />
                <v-text-field v-else v-model="userForm.newPassword" type="password" label="New password (leave blank to keep the current password)" minlength="12" autocomplete="new-password" variant="outlined" />
                <v-switch v-if="editingUser" v-model="userForm.enabled" label="Account active" color="primary" hide-details />
                <div class="d-flex justify-end ga-2 mt-3"><v-btn variant="text" @click="userDialog = false">Cancel</v-btn><v-btn type="submit" color="primary">{{ editingUser ? 'Save changes' : 'Create account' }}</v-btn></div>
              </v-form>
            </v-card></v-dialog>
          </template>

          <template v-else-if="page === 'Audit Logs'">
            <v-card class="pa-4 scroll-table"><v-table density="compact">
              <thead><tr><th>Date and time</th><th>User</th><th>Action</th><th>Affected record</th><th>Details</th></tr></thead>
              <tbody><tr v-for="item in auditLogs" :key="item.id"><td>{{ item.occurredAt?.replace('T', ' ').slice(0, 19) }}</td>
                <td>{{ item.actorUsername }}</td><td>{{ auditActionLabel(item.action) }}</td><td>{{ auditEntityLabel(item.entityType) }}<span v-if="item.entityId"> #{{ item.entityId }}</span></td><td>{{ auditDetails(item) }}</td></tr>
                <tr v-if="!auditLogs.length"><td colspan="5" class="text-center muted py-6">No audit activity recorded yet.</td></tr>
              </tbody>
            </v-table></v-card>
          </template>

          <template v-else-if="page === 'My Account'">
            <template v-if="studentPortal.summary">
              <v-row>
                <v-col cols="12" md="8">
                  <v-card class="pa-5 h-100">
                    <h3>My profile</h3>
                    <v-list density="compact">
                      <v-list-item title="Student ID" :subtitle="studentPortal.summary.student.studentNumber" />
                      <v-list-item title="Full name" :subtitle="studentPortal.summary.student.fullName" />
                      <v-list-item title="Course" :subtitle="studentPortal.summary.student.course || '—'" />
                      <v-list-item title="Year level" :subtitle="studentPortal.summary.student.yearLevel || '—'" />
                      <v-list-item title="Section" :subtitle="studentPortal.summary.student.section || '—'" />
                    </v-list>
                  </v-card>
                </v-col>
                <v-col cols="12" md="4">
                  <v-card class="pa-5 h-100">
                    <h3>My unpaid balance</h3>
                    <div class="metric-value mt-4">{{ money(studentPortal.summary.unpaidBalance) }}</div>
                    <p class="muted mt-3">Attendance: {{ studentPortal.summary.attendance.present }} present, {{ studentPortal.summary.attendance.late }} late, {{ studentPortal.summary.attendance.absent }} absent, {{ studentPortal.summary.attendance.excused }} excused.</p>
                  </v-card>
                </v-col>
              </v-row>
              <v-card class="pa-4 mt-4 scroll-table">
                <h3 class="mb-3">My fines and payments</h3>
                <v-table density="compact">
                  <thead><tr><th>Date</th><th>Type</th><th>Amount</th><th>Description</th><th>Reference</th></tr></thead>
                  <tbody>
                    <tr v-for="item in studentPortal.ledger" :key="item.id">
                      <td>{{ item.createdAt?.slice(0, 10) }}</td><td>{{ typeLabel(item.kind) }}</td>
                      <td>{{ money(item.amount) }}</td><td>{{ item.description }}</td><td>{{ item.referenceNumber }}</td>
                    </tr>
                    <tr v-if="!studentPortal.ledger.length"><td colspan="5" class="text-center muted py-6">No fines or payments recorded.</td></tr>
                  </tbody>
                </v-table>
              </v-card>
              <v-card class="pa-4 mt-4 scroll-table">
                <h3 class="mb-3">My attendance</h3>
                <v-table density="compact">
                  <thead><tr><th>Date</th><th>Activity</th><th>Check-in time</th><th>Status</th></tr></thead>
                  <tbody>
                    <tr v-for="(item, index) in studentPortal.attendance" :key="`${item.scannedAt}-${index}`">
                      <td>{{ item.activityDate }}</td><td>{{ item.activityName }}</td><td>{{ item.scannedAt?.replace('T', ' ') }}</td><td>{{ item.status }}</td>
                    </tr>
                    <tr v-if="!studentPortal.attendance.length"><td colspan="4" class="text-center muted py-6">No attendance records yet.</td></tr>
                  </tbody>
                </v-table>
              </v-card>
            </template>
            <v-card v-else class="pa-6">
              <h3>Student Portal</h3>
              <p class="muted mt-3">Your student account is not linked to a student record. Contact the department administrator.</p>
            </v-card>
          </template>
        </div>
      </v-main>
    </template>

    <v-dialog v-model="reverseDialog" max-width="520">
      <v-card class="pa-5">
        <h3>Correct transaction</h3>
        <p class="muted mt-2">This adds a linked entry that offsets this transaction. The original record remains recorded.</p>
        <v-form class="mt-3" @submit.prevent="reverseTransaction">
          <v-textarea v-model="reverseReason" label="Reason for correction" maxlength="200" required variant="outlined" rows="3" />
          <div class="d-flex justify-end ga-2"><v-btn variant="text" @click="reverseDialog = false">Cancel</v-btn><v-btn type="submit" color="error">Add correction</v-btn></div>
        </v-form>
      </v-card>
    </v-dialog>
  </v-app>
</template>
