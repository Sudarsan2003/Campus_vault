// After a failed submit, move keyboard focus to the first field with an error.
export const focusFirstInvalid = () => {
  requestAnimationFrame(() => {
    document.querySelector('[aria-invalid="true"]')?.focus();
  });
};