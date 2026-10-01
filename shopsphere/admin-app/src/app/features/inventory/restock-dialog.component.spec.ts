import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { RestockDialogComponent } from './restock-dialog.component';

describe('RestockDialogComponent', () => {
  function create() {
    const dialogRef = jasmine.createSpyObj<MatDialogRef<RestockDialogComponent, number>>('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [RestockDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { productId: 9, productName: 'USB-C Hub', availableQuantity: 3, reservedQuantity: 3, sellableQuantity: 0 },
        },
      ],
    });
    const fixture = TestBed.createComponent(RestockDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, dialogRef };
  }

  it('closes with a valid quantity', () => {
    const { component, dialogRef } = create();
    component.form.controls.quantity.setValue(40);

    component.confirm();

    expect(dialogRef.close).toHaveBeenCalledOnceWith(40);
  });

  it('rejects zero, fractions and empty input', () => {
    const { component, dialogRef } = create();
    for (const value of [null, 0, 2.5]) {
      component.form.controls.quantity.setValue(value);
      component.confirm();
    }
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
