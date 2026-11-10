/** Backend rule: @Digits(integer = 7, fraction = 2). */
export function hasAtMostTwoDecimals(value: number): boolean {
  return Math.abs(value * 100 - Math.round(value * 100)) < 1e-6;
}
