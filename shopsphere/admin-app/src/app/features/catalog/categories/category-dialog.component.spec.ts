import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { Category } from '../catalog.models';
import { buildCategory } from '../catalog.testing';
import { CategoryApiService } from '../category-api.service';
import { CategoryDialogComponent, CategoryDialogData } from './category-dialog.component';

describe('CategoryDialogComponent', () => {
  let fixture: ComponentFixture<CategoryDialogComponent>;
  let component: CategoryDialogComponent;
  let api: jasmine.SpyObj<CategoryApiService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<CategoryDialogComponent, Category>>;

  function setup(data: CategoryDialogData = {}): void {
    api = jasmine.createSpyObj<CategoryApiService>('CategoryApiService', ['create', 'update']);
    dialogRef = jasmine.createSpyObj<MatDialogRef<CategoryDialogComponent, Category>>('MatDialogRef', ['close']);

    TestBed.configureTestingModule({
      imports: [CategoryDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: CategoryApiService, useValue: api },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(CategoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('creates a category and closes with it', () => {
    setup();
    const saved = buildCategory({ id: 9, name: 'Garden' });
    api.create.and.returnValue(of(saved));

    component.form.setValue({ name: '  Garden ', description: '' });
    component.save();

    expect(api.create).toHaveBeenCalledOnceWith({ name: 'Garden', description: null });
    expect(dialogRef.close).toHaveBeenCalledOnceWith(saved);
  });

  it('pre-fills and updates an existing category', () => {
    const existing = buildCategory({ id: 3, name: 'Toys', description: 'Games' });
    setup({ category: existing });
    api.update.and.returnValue(of({ ...existing, name: 'Toys & Games' }));

    expect(component.form.getRawValue()).toEqual({ name: 'Toys', description: 'Games' });

    component.form.controls.name.setValue('Toys & Games');
    component.save();

    expect(api.update).toHaveBeenCalledOnceWith(3, { name: 'Toys & Games', description: 'Games' });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('flags a duplicate name (409) on the field and stays open', () => {
    setup();
    api.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    component.form.setValue({ name: 'Electronics', description: '' });
    component.save();
    fixture.detectChanges();

    expect(component.form.controls.name.getError('server')).toContain('already exists');
    expect(fixture.nativeElement.textContent).toContain('A category with this name already exists.');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.saving()).toBeFalse();
  });

  it('does not submit without a name', () => {
    setup();

    component.save();

    expect(api.create).not.toHaveBeenCalled();
    expect(component.form.controls.name.touched).toBeTrue();
  });
});
