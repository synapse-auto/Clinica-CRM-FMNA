import { describe, expect, it } from 'vitest';
import { formatSize } from './ImportacaoCsvModal';

describe('ImportacaoCsvModal', () => {
  it('should_format_files_larger_than_one_megabyte_using_megabytes', () => {
    expect(formatSize(1_397_448)).toBe('1.3 MB');
  });
});
