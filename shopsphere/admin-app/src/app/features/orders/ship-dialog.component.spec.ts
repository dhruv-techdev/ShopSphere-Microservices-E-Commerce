import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { ShipRequest } from './order.models';
import { ShipDialogComponent } from './ship-dialog.component';

describe('ShipDialogComponent', () => {
  let fixture: ComponentFixture<ShipDialogComponent>;
  let component: ShipDialogComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ShipDialogComponent, ShipRequest>>;

  beforeEach(() => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<ShipDialogComponent, ShipRequest>>('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [ShipDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { orderId: 42 } },
      ],
    });
    fixture = TestBed.createComponent(ShipDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('sends nulls when both fields are blank (server defaults apply)', () => {
    component.confirm();

    expect(dialogRef.close).toHaveBeenCalledOnceWith({ carrier: null, trackingNumber: null });
  });

  it('trims the carrier and upper-cases the tracking number', () => {
    component.form.setValue({ carrier: '  UPS ', trackingNumber: ' 1z999aa10123456784 ' });

    component.confirm();

    expect(dialogRef.close).toHaveBeenCalledOnceWith({ carrier: 'UPS', trackingNumber: '1Z999AA10123456784' });
  });

  it('rejects tracking numbers with unsupported characters', () => {
    component.form.controls.trackingNumber.setValue('ab#1');

    component.confirm();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.form.controls.trackingNumber.hasError('pattern')).toBeTrue();
  });
});
