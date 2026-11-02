export function setRequestFailure(errorState, noticeState, failure, friendlyError) {
  noticeState.value = '';
  errorState.value = friendlyError(failure);
}
