import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Product } from '../catalog.models';
import { buildCategory, buildProduct } from '../catalog.testing';
import { CategoryApiService } from '../category-api.service';
import { ProductApiService } from '../product-api.service';
import { ProductFormComponent, integer, maxDecimals } from './product-form.component';

describe('ProductFormComponent', () => {
  let fixture: ComponentFixture<ProductFormComponent>;
  let component: ProductFormComponent;
  let productApi: jasmine.SpyObj<ProductApiService>;
  let categoryApi: jasmine.SpyObj<CategoryApiService>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let notifier: jasmine.SpyObj<Notifier>;
  let router: Router;

  function setup(id?: string, product$: Observable<Product> = of(buildProduct())): void {
    productApi = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['get', 'create', 'update', 'delete']);
    productApi.get.and.returnValue(product$);
    categoryApi = jasmine.createSpyObj<CategoryApiService>('CategoryApiService', ['list']);
    categoryApi.list.and.returnValue(of([buildCategory(), buildCategory({ id: 4, name: 'Books' })]));
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirm']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [ProductFormComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ProductApiService, useValue: productApi },
        { provide: CategoryApiService, useValue: categoryApi },
        { provide: ConfirmService, useValue: confirm },
        { provide: Notifier, useValue: notifier },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ProductFormComponent);
    component = fixture.componentInstance;
    if (id !== undefined) {
      fixture.componentRef.setInput('id', id);
    }
    fixture.detectChanges();
  }

  function text(testId: string): string | undefined {
    const el: HTMLElement | null = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    return el?.textContent?.trim();
  }

  describe('create mode', () => {
    it('creates a product from the trimmed form values', () => {
      setup();
      productApi.create.and.returnValue(of(buildProduct({ id: 9, name: 'Keyboard' })));

      component.form.setValue({
        name: '  Keyboard ',
        description: '   ',
        price: 49.5,
        stockQuantity: 3,
        categoryId: 4,
        active: false,
      });
      component.save();

      expect(productApi.create).toHaveBeenCalledOnceWith({
        name: 'Keyboard',
        description: null,
        price: 49.5,
        stockQuantity: 3,
        categoryId: 4,
        active: false,
      });
      expect(productApi.get).not.toHaveBeenCalled();
      expect(notifier.success).toHaveBeenCalledWith('Created "Keyboard".');
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('does not submit an invalid form', () => {
      setup();

      component.form.patchValue({ name: 'Keyboard', price: 0 });
      component.save();

      expect(productApi.create).not.toHaveBeenCalled();
      expect(component.form.controls.price.hasError('min')).toBeTrue();
    });

    it('shows server validation messages on the matching fields', () => {
      setup();
      productApi.create.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { message: 'Request validation failed', fieldErrors: { name: 'Product name is required' } },
            }),
        ),
      );

      component.form.patchValue({ name: 'x', price: 10, stockQuantity: 1 });
      component.save();
      fixture.detectChanges();

      expect(component.form.controls.name.getError('server')).toBe('Product name is required');
      expect(text('form-error')).toBe('Please fix the highlighted fields.');
      expect(router.navigate).not.toHaveBeenCalled();
      expect(component.saving()).toBeFalse();
    });
  });

  describe('edit mode', () => {
    it('loads the product and saves changes with PUT', () => {
      setup('5');

      expect(productApi.get).toHaveBeenCalledOnceWith(5);
      expect(component.form.getRawValue()).toEqual({
        name: 'Wireless Mouse',
        description: 'Bluetooth, ergonomic',
        price: 29.99,
        stockQuantity: 12,
        categoryId: 3,
        active: true,
      });

      productApi.update.and.returnValue(of(buildProduct({ price: 24.99 })));
      component.form.controls.price.setValue(24.99);
      component.save();

      expect(productApi.update).toHaveBeenCalledOnceWith(5, jasmine.objectContaining({ price: 24.99, categoryId: 3 }));
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('explains a missing product', () => {
      setup('5', throwError(() => new HttpErrorResponse({ status: 404 })));

      expect(text('load-error')).toContain('Product not found');
    });

    it('rejects a non-numeric id without calling the API', () => {
      setup('abc');

      expect(productApi.get).not.toHaveBeenCalled();
      expect(text('load-error')).toContain('Product not found');
    });

    it('deletes after confirmation and returns to the list', () => {
      setup('5');
      confirm.confirm.and.returnValue(of(true));
      productApi.delete.and.returnValue(of(undefined));

      component.delete();

      expect(productApi.delete).toHaveBeenCalledOnceWith(5);
      expect(notifier.success).toHaveBeenCalledWith('Deleted "Wireless Mouse".');
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });
  });

  describe('validators', () => {
    it('maxDecimals allows up to N places', () => {
      const v = maxDecimals(2);
      expect(v({ value: 10.25 } as never)).toBeNull();
      expect(v({ value: 10.255 } as never)).toEqual({ maxDecimals: { places: 2 } });
      expect(v({ value: null } as never)).toBeNull();
    });

    it('integer rejects fractions', () => {
      expect(integer({ value: 3 } as never)).toBeNull();
      expect(integer({ value: 3.5 } as never)).toEqual({ integer: true });
    });
  });
});
